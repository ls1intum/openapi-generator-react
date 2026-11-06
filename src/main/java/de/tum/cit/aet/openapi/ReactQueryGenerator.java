/*
 * Copyright (c) 2026 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.AbstractTypeScriptClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * OpenAPI Generator for React applications: typed models, a small {@code fetch} client and
 * <a href="https://tanstack.com/query">TanStack Query</a> hooks.
 *
 * <p>Per API tag the generator writes two files:</p>
 * <ol>
 *   <li>{@code <tag>-api.ts}: one plain async function per operation. It takes one {@code params} object that holds
 *       the path, query and header parameters and the {@code body}, and returns the parsed response.</li>
 *   <li>{@code <tag>-hooks.ts}: for every GET a {@code queryKey} function, a {@code queryOptions} function and a
 *       {@code useQuery} hook, and for every POST, PUT, PATCH and DELETE a {@code useMutation} hook.</li>
 * </ol>
 *
 * <p>Next to them the generator writes {@code api/client.ts} (the {@code fetch} wrapper with {@code configureApi} and
 * {@code ApiError}), {@code api/query-params.ts} and {@code api/hook-options.ts}, one file per model in
 * {@code models/}, and the index files.</p>
 *
 * <p>The generator hooks into three lifecycle stages of the OpenAPI Generator framework:</p>
 * <ol>
 *   <li>{@link #processOpts()} registers the supporting files.</li>
 *   <li>{@link #postProcessAllModels(Map)} prepares the models: property keys, unions and the imports of each
 *       model file.</li>
 *   <li>{@link #postProcessOperationsWithModels(OperationsMap, List)} builds the request code of each operation and
 *       the imports of its files.</li>
 * </ol>
 *
 * <p>What the generator does not support yet fails the generation with a message that names the operation, instead
 * of producing code that does not work: request bodies other than JSON, cookie parameters and query parameter styles
 * that OpenAPI does not allow in a query.</p>
 */
public class ReactQueryGenerator extends AbstractTypeScriptClientCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactQueryGenerator.class);

    /** Generator name used by the OpenAPI Generator SPI and CLI. */
    public static final String GENERATOR_NAME = "react-query";

    /** The key of the request body in a {@code params} object. */
    private static final String BODY_KEY = "body";

    /** HTTP methods whose operations become a mutation hook. */
    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    public ReactQueryGenerator() {
        super();

        embeddedTemplateDir = templateDir = GENERATOR_NAME;
        outputFolder = "generated-code" + File.separator + GENERATOR_NAME;
        apiPackage = "api";
        modelPackage = "models";

        modelTemplateFiles.clear();
        modelTemplateFiles.put("model.mustache", ".ts");
        apiTemplateFiles.clear();
        apiTemplateFiles.put("api.mustache", "-api.ts");
        apiTemplateFiles.put("hooks.mustache", "-hooks.ts");

        // JSON has no sets: a response arrives as an array, and JSON.stringify sends a Set as {}.
        // Models are immutable, so a collection is a ReadonlyArray; a mutable array is assignable to it.
        for (String key : List.of("array", "Array", "set", "Set")) {
            typeMapping.put(key, "ReadonlyArray");
        }
        typeMapping.put("object", "Record<string, unknown>");
        typeMapping.put("Map", "Record<string, unknown>");
        typeMapping.put("map", "Record<string, unknown>");
        typeMapping.put("AnyType", "unknown");
        languageSpecificPrimitives.add("unknown");
        typeMapping.put("binary", "Blob");
        typeMapping.put("file", "Blob");
        typeMapping.put("File", "Blob");

        // The parent offers an option for each of these; the generated code does not use them.
        cliOptions.removeIf(option -> Set.of("supportsES6", "npmName", "npmVersion", "snapshot").contains(option.getOpt()));
    }

    @Override
    public String getName() {
        return GENERATOR_NAME;
    }

    @Override
    public String getHelp() {
        return "Generates a React client: readonly models, a fetch client and TanStack Query hooks.";
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        supportingFiles.clear();
        String api = apiPackage().replace('.', File.separatorChar);
        String models = modelPackage().replace('.', File.separatorChar);
        supportingFiles.add(new SupportingFile("client.mustache", api, "client.ts"));
        supportingFiles.add(new SupportingFile("query-params.mustache", api, "query-params.ts"));
        supportingFiles.add(new SupportingFile("hook-options.mustache", api, "hook-options.ts"));
        supportingFiles.add(new SupportingFile("api-index.mustache", api, "index.ts"));
        supportingFiles.add(new SupportingFile("models-index.mustache", models, "index.ts"));
        supportingFiles.add(new SupportingFile("root-index.mustache", "", "index.ts"));
        additionalProperties.put("apiPackageName", apiPackage());
        additionalProperties.put("modelPackageName", modelPackage());
    }

    // =============================================================================================
    // Types
    // =============================================================================================

    /**
     * Types a binary schema as {@code Blob}, which is what {@code Response.blob()} returns, an array as
     * {@code readonly T[]} and a map as {@code Readonly<Record<string, T>>}, like the models.
     */
    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isFileSchema(p) || ModelUtils.isBinarySchema(p)) {
            return "Blob";
        }
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> items = ModelUtils.getSchemaItems(p);
            String nullable = Boolean.TRUE.equals(items.getNullable()) ? " | null" : "";
            return readonlyArrayOf(getTypeDeclaration(unaliasSchema(items)) + nullable);
        }
        if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = getSchemaAdditionalProperties(p);
            String nullable = Boolean.TRUE.equals(inner.getNullable()) ? " | null" : "";
            return recordOf(getTypeDeclaration(unaliasSchema(inner)) + nullable);
        }
        return super.getTypeDeclaration(p);
    }

    /** Types the parameters that the parent types as {@code Array<T>} and {@code { [key: string]: T; }} the same way. */
    @Override
    protected String getParameterDataType(Parameter parameter, Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            return readonlyArrayOf(getParameterDataType(parameter, ModelUtils.getSchemaItems(p)));
        }
        if (ModelUtils.isMapSchema(p)) {
            return recordOf(getParameterDataType(parameter, ModelUtils.getAdditionalProperties(p)));
        }
        return super.getParameterDataType(parameter, p);
    }

    /** Writes an array type as {@code readonly T[]}, with parentheses where the item type needs them. */
    private static String readonlyArrayOf(String item) {
        boolean needsParentheses = item.contains(" | ") || item.contains(" & ") || item.startsWith("readonly ") || item.contains("=>");
        return "readonly " + (needsParentheses ? "(" + item + ")" : item) + "[]";
    }

    private static String recordOf(String value) {
        return "Readonly<Record<string, " + value + ">>";
    }

    // =============================================================================================
    // Models
    // =============================================================================================

    /**
     * Prepares the models for {@code model.mustache}: the key of each property ({@code x-property-name}), a
     * {@code oneOf} model as a union ({@code x-is-one-of}, {@code x-one-of-type}), the imports of each model file
     * ({@code x-imports}) and the file name ({@code x-file-name}).
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);

        Set<String> knownModels = new HashSet<>();
        result.values().forEach(models -> models.getModels().forEach(model -> knownModels.add(model.getModel().classname)));

        for (ModelsMap modelsMap : result.values()) {
            for (ModelMap modelMap : modelsMap.getModels()) {
                CodegenModel model = modelMap.getModel();
                requireWhatAncestorsRequire(model);
                model.vendorExtensions.put("x-file-name", classFilename(model.classname));

                List<String> referenced = new ArrayList<>();
                if (isUnion(model)) {
                    model.vendorExtensions.put("x-is-one-of", true);
                    model.vendorExtensions.put("x-one-of-type", unionOf(model));
                    model.getComposedSchemas().getOneOf().forEach(branch -> referenced.add(branch.dataType));
                    if (!declaredProperties(model).isEmpty()) {
                        LOGGER.warn("The oneOf schema {} declares properties of its own, which its union type does not carry", model.name);
                    }
                } else if (model.parentModel != null && isUnion(model.parentModel)) {
                    LOGGER.warn("The schema {} extends the oneOf schema {}, which a TypeScript interface cannot do; it is generated without the parent",
                            model.name, model.parentModel.name);
                    model.parent = null;
                }
                if (model.parent != null) {
                    referenced.add(model.parent);
                }
                for (CodegenProperty property : model.vars) {
                    property.vendorExtensions.put("x-property-name", TypeScriptSnippets.toPropertyKey(property.baseName));
                    // unknown already includes null.
                    property.vendorExtensions.put("x-nullable", property.isNullable && !"unknown".equals(property.dataType));
                    referenced.add(property.dataType);
                    if (property.items != null) {
                        referenced.add(property.items.dataType);
                    }
                }
                Set<String> imports = new TreeSet<>();
                referenced.forEach(type -> imports.addAll(TypeScriptSnippets.referencedModels(type, knownModels)));
                imports.remove(model.classname);
                model.vendorExtensions.put("x-imports", imports.stream().map(ReactQueryGenerator::tsImport).toList());
                model.vendorExtensions.put("x-has-imports", !imports.isEmpty());
            }
        }
        return result;
    }

    private static boolean isUnion(CodegenModel model) {
        return model.getComposedSchemas() != null && model.getComposedSchemas().getOneOf() != null
                && !model.getComposedSchemas().getOneOf().isEmpty();
    }

    /**
     * Renders the branches of a {@code oneOf} model as a union, e.g. {@code A | string | ReadonlyArray<B> | null}. An
     * inline enum branch contributes its values rather than its base type, which would absorb the other members.
     */
    private static String unionOf(CodegenModel model) {
        Set<String> members = new LinkedHashSet<>();
        for (CodegenProperty branch : model.getComposedSchemas().getOneOf()) {
            if (branch.isEnum && branch._enum != null) {
                // Only numbers and booleans are bare literals; a string with a format, such as a date, needs quotes too.
                branch._enum.forEach(value -> members.add(branch.isNumeric || branch.isBoolean ? value : TypeScriptSnippets.stringLiteral(value)));
            } else {
                members.add(branch.dataType);
            }
        }
        if (model.isNullable) {
            members.add("null");
        }
        return String.join(" | ", members);
    }

    /** Copies the properties a {@code oneOf} root declares itself; the parent's model also holds those of its branches. */
    private List<CodegenProperty> declaredProperties(CodegenModel union) {
        Schema<?> schema = ModelUtils.getSchema(openAPI, union.name);
        Set<String> declared = schema != null && schema.getProperties() != null ? schema.getProperties().keySet() : Set.of();
        return union.vars.stream().filter(property -> declared.contains(property.baseName)).toList();
    }

    /**
     * Makes a subtype's property required when an ancestor interface requires it. A schema built as
     * {@code allOf: [{$ref: Parent}, {properties: {...}}]} becomes an interface that {@code extends Parent}; when the
     * inline part restates a required parent property without requiring it (typically the discriminator), the child
     * would declare {@code type?: string} against the parent's {@code type: string}, which TypeScript rejects.
     */
    private static void requireWhatAncestorsRequire(CodegenModel model) {
        Set<String> required = new HashSet<>();
        for (CodegenModel ancestor = model.parentModel; ancestor != null; ancestor = ancestor.parentModel) {
            for (CodegenProperty property : ancestor.vars) {
                if (property.required) {
                    required.add(property.baseName);
                }
            }
        }
        for (CodegenProperty property : model.vars) {
            if (required.contains(property.baseName)) {
                property.required = true;
            }
        }
    }

    // =============================================================================================
    // Operations
    // =============================================================================================

    /**
     * Builds what {@code api.mustache} and {@code hooks.mustache} print for each operation, in vendor extensions:
     * the name and the members of its {@code params} interface, the request code, the doc comment, and whether the
     * operation becomes a query or a mutation. Per file it sets the imports and the TanStack Query names to import.
     */
    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);

        Set<String> knownModels = new HashSet<>();
        allModels.forEach(model -> knownModels.add(model.getModel().classname));

        OperationMap operations = result.getOperations();
        List<CodegenOperation> ops = operations.getOperation();
        boolean hasQueries = false;
        boolean hasMutations = false;
        boolean hasQueryParams = false;
        Set<String> apiTypes = new TreeSet<>();
        for (CodegenOperation op : ops) {
            rejectWhatIsNotSupported(op);
            prepareOperation(op);
            String paramsName = (String) op.vendorExtensions.get("x-params-name");
            if (knownModels.contains(paramsName)) {
                throw new IllegalArgumentException("Operation " + op.operationId + " has the arguments type " + paramsName
                        + ", which is also the name of a schema. Rename the operation or the schema, or set modelNameSuffix.");
            }
            hasQueryParams |= !op.queryParams.isEmpty();
            hasQueries |= Boolean.TRUE.equals(op.vendorExtensions.get("x-is-query"));
            hasMutations |= Boolean.TRUE.equals(op.vendorExtensions.get("x-is-mutation"));
            apiTypes.addAll(TypeScriptSnippets.referencedModels(op.returnType, knownModels));
            for (Object member : (List<?>) op.vendorExtensions.get("x-members")) {
                apiTypes.addAll(TypeScriptSnippets.referencedModels((String) ((Map<?, ?>) member).get("type"), knownModels));
            }
        }

        Set<String> hookTypes = new TreeSet<>();
        for (CodegenOperation op : ops) {
            if (Boolean.TRUE.equals(op.vendorExtensions.get("x-is-query")) || Boolean.TRUE.equals(op.vendorExtensions.get("x-is-mutation"))) {
                hookTypes.addAll(TypeScriptSnippets.referencedModels(op.returnType, knownModels));
            }
        }

        boolean hasDeprecatedHooks = false;
        CodegenOperation lastHook = null;
        List<String> hookFunctions = new ArrayList<>();
        List<String> hookParamTypes = new ArrayList<>();
        Set<String> hookOptionNames = new TreeSet<>();
        for (CodegenOperation op : ops) {
            boolean isQuery = Boolean.TRUE.equals(op.vendorExtensions.get("x-is-query"));
            boolean isMutation = Boolean.TRUE.equals(op.vendorExtensions.get("x-is-mutation"));
            if (isQuery || isMutation) {
                lastHook = op;
                hasDeprecatedHooks |= op.isDeprecated;
                hookFunctions.add((String) op.vendorExtensions.get("x-function-name"));
                if (Boolean.TRUE.equals(op.vendorExtensions.get("x-has-params"))) {
                    hookParamTypes.add((String) op.vendorExtensions.get("x-params-name"));
                }
                hookOptionNames.add(isQuery ? "QueryHookOptions" : "MutationHookOptions");
            }
        }

        String tagName = operations.getClassname().replaceFirst("Api$", "");
        result.put("hasQueries", hasQueries);
        result.put("hasMutations", hasMutations);
        result.put("hasHooks", hasQueries || hasMutations);
        result.put("hasQueryParams", hasQueryParams);
        result.put("queryKeyName", Names.toCamelCase(Names.toKebabCase(tagName)) + "QueryKey");
        result.put("queryKeyValue", TypeScriptSnippets.stringLiteral(Names.toCamelCase(Names.toKebabCase(tagName))));
        if (lastHook != null) {
            lastHook.vendorExtensions.put("x-is-last-hook", true);
        }
        result.put("hasDeprecatedHooks", hasDeprecatedHooks);

        List<String> tanstackImports = new ArrayList<>();
        if (hasQueries) {
            tanstackImports.add("queryOptions");
        }
        if (hasMutations) {
            tanstackImports.add("useMutation");
        }
        if (hasQueries) {
            tanstackImports.add("useQuery");
        }
        result.put("tanstackImports", String.join(", ", tanstackImports));
        result.put("apiImports", apiTypes.stream().map(ReactQueryGenerator::tsImport).toList());
        result.put("hookImports", hookTypes.stream().map(ReactQueryGenerator::tsImport).toList());
        result.put("hookFunctions", String.join(", ", hookFunctions));
        result.put("hookParamTypes", String.join(", ", hookParamTypes));
        result.put("hasHookParamTypes", !hookParamTypes.isEmpty());
        result.put("hookOptionNames", String.join(", ", hookOptionNames));
        result.put("apiFileName", toApiFilename(tagName));
        return result;
    }

    /** Fails the generation for an operation whose request the generated code cannot send yet. */
    private static void rejectWhatIsNotSupported(CodegenOperation op) {
        String where = " of operation " + op.operationId + " (" + op.httpMethod + " " + op.path + ")";
        if (op.getHasFormParams()) {
            throw new IllegalArgumentException("The request body" + where + " is a form or multipart body, which this generator does not support yet");
        }
        if (op.bodyParam != null && (op.bodyParam.isFile || op.bodyParam.isBinary || !TypeScriptSnippets.consumesJson(op))) {
            throw new IllegalArgumentException("The request body" + where + " is not JSON, which this generator does not support yet");
        }
        if (op.getHasCookieParams()) {
            throw new IllegalArgumentException("Cookie parameters" + where + " are not supported: the browser sends cookies itself");
        }
    }

    /** Sets the vendor extensions of one operation, see {@link #postProcessOperationsWithModels}. */
    private void prepareOperation(CodegenOperation op) {
        String function = op.nickname;
        Set<String> keys = new HashSet<>(Set.of(BODY_KEY));
        Map<String, String> pathKeys = new HashMap<>();
        List<Map<String, Object>> members = new ArrayList<>();
        for (CodegenParameter param : op.pathParams) {
            Map<String, Object> member = member(keys, param);
            pathKeys.put(param.baseName, (String) member.get("key"));
            members.add(member);
        }
        List<String> queryLines = new ArrayList<>();
        for (CodegenParameter param : op.queryParams) {
            Map<String, Object> member = member(keys, param);
            members.add(member);
            queryLines.add("appendQueryParam(query, " + TypeScriptSnippets.stringLiteral(param.baseName) + ", params." + member.get("key")
                    + TypeScriptSnippets.queryStyleArguments(op, param) + ");");
        }
        List<String> headerLines = new ArrayList<>();
        for (CodegenParameter param : op.headerParams) {
            Map<String, Object> member = member(keys, param);
            members.add(member);
            String access = "params." + member.get("key");
            String value = param.isArray ? access + ".join(',')" : param.isString ? access : "String(" + access + ")";
            String set = "headers.set(" + TypeScriptSnippets.stringLiteral(param.baseName) + ", " + value + ");";
            headerLines.add(param.required ? set : "if (" + access + " !== undefined) {\n        " + set + "\n    }");
        }
        if (op.bodyParam != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("key", BODY_KEY);
            body.put("type", op.bodyParam.dataType);
            body.put("optional", !op.bodyParam.required);
            body.put("hasDoc", op.bodyParam.description != null && !op.bodyParam.description.isBlank());
            body.put("doc", oneLine(op.bodyParam.description));
            members.add(body);
        }

        boolean allOptional = members.stream().noneMatch(member -> Boolean.FALSE.equals(member.get("optional")));
        TypeScriptSnippets.ResponseKind response = TypeScriptSnippets.ResponseKind.of(op);
        String returnType = switch (response) {
            case VOID -> "undefined";
            case JSON, TEXT, BLOB -> op.returnType;
        };

        op.vendorExtensions.put("x-function-name", function);
        op.vendorExtensions.put("x-hook-name", "use" + Names.toPascalCase(function));
        op.vendorExtensions.put("x-params-name", Names.toPascalCase(function) + "Params");
        op.vendorExtensions.put("x-has-params", !members.isEmpty());
        op.vendorExtensions.put("x-params-optional", allOptional);
        op.vendorExtensions.put("x-members", members);
        op.vendorExtensions.put("x-return-type", returnType);
        op.vendorExtensions.put("x-response-type", TypeScriptSnippets.stringLiteral(response.literal()));
        op.vendorExtensions.put("x-path", TypeScriptSnippets.buildPathLiteral(op, pathKeys));
        op.vendorExtensions.put("x-query-lines", queryLines);
        op.vendorExtensions.put("x-header-lines", headerLines);
        op.vendorExtensions.put("x-has-query", !queryLines.isEmpty());
        op.vendorExtensions.put("x-has-headers", !headerLines.isEmpty());
        op.vendorExtensions.put("x-has-body", op.bodyParam != null);
        op.vendorExtensions.put("x-is-query", "GET".equals(op.httpMethod));
        op.vendorExtensions.put("x-is-mutation", MUTATION_METHODS.contains(op.httpMethod));

        List<String> doc = new ArrayList<>(TypeScriptSnippets.docLines(op.summary));
        List<String> notes = TypeScriptSnippets.docLines(op.notes);
        if (!notes.isEmpty() && !doc.isEmpty()) {
            doc.add("");
        }
        doc.addAll(notes);
        if (op.isDeprecated) {
            if (!doc.isEmpty()) {
                doc.add("");
            }
            doc.add("@deprecated");
        }
        op.vendorExtensions.put("x-doc-single", doc.size() == 1);
        op.vendorExtensions.put("x-doc-first", doc.isEmpty() ? "" : doc.get(0));
        op.vendorExtensions.put("x-doc-lines", doc.stream().map(line -> line.isEmpty() ? " *" : " * " + line).toList());
        op.vendorExtensions.put("x-has-doc", !doc.isEmpty());
    }

    /**
     * Describes a parameter as a member of the {@code params} interface: the unique key of the member, its type, whether
     * it is optional and its description. The key is the parent's identifier for the wire name without the {@code _}
     * that escapes a reserved word, since a property may have that name; a digit suffix keeps it unique within the
     * operation, and {@code body} is taken by the request body.
     */
    private Map<String, Object> member(Set<String> keys, CodegenParameter param) {
        String identifier = super.toParamName(param.baseName);
        String preferred = identifier.startsWith("_") && isReservedWord(identifier.substring(1)) ? identifier.substring(1) : identifier;

        Map<String, Object> member = new LinkedHashMap<>();
        member.put("key", TypeScriptSnippets.allocate(keys, preferred));
        member.put("type", param.dataType);
        member.put("optional", !param.required);
        member.put("hasDoc", param.description != null && !param.description.isBlank());
        member.put("doc", oneLine(param.description));
        return member;
    }

    /** Puts a description on one line of a doc comment, keeping {@code *}{@code /} from ending the comment early. */
    private static String oneLine(String description) {
        return description == null ? "" : description.strip().replace("*/", "*\\/").replaceAll("\\s*\\R\\s*", " ");
    }

    // =============================================================================================
    // Naming
    // =============================================================================================

    /** Converts a schema name to the kebab-case filename of its model, derived from the class name. */
    @Override
    public String toModelFilename(String name) {
        return classFilename(toModelName(name));
    }

    private static String classFilename(String className) {
        return Names.toKebabCase(className);
    }

    private static Map<String, String> tsImport(String className) {
        return Map.of("classname", className, "filename", classFilename(className));
    }

    /** Converts an API tag name to a kebab-case filename, e.g. {@code job-resource}. */
    @Override
    public String toApiFilename(String name) {
        return Names.toKebabCase(name);
    }

    /** Converts an API tag name to a PascalCase class name with the {@code Api} suffix, e.g. {@code JobResourceApi}. */
    @Override
    public String toApiName(String name) {
        return StringUtils.camelize(name) + "Api";
    }

    /**
     * Cleans up operation IDs by stripping leading underscores and trailing digits that a spec sometimes adds to
     * disambiguate overloaded endpoints.
     */
    @Override
    public String toOperationId(String operationId) {
        String name = super.toOperationId(operationId);
        String normalized = name.replaceFirst("^_+", "").replaceFirst("\\d+$", "");
        return normalized.isBlank() ? "operation" : normalized;
    }
}
