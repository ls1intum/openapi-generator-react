/*
 * Copyright (c) 2026 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the TypeScript snippets that the templates print for an operation or a parameter and stores them in vendor
 * extensions. The snippets depend only on the codegen model, not on the generator's state.
 */
final class TypeScriptSnippets {

    private static final Set<String> QUERY_STYLES = Set.of("form", "spaceDelimited", "pipeDelimited", "deepObject");
    private static final Pattern JSON_MEDIA_TYPE = Pattern.compile("(?i)application/([^;]+\\+)?json(\\s*;.*)?");
    /** An ASCII identifier, which TypeScript accepts as a property name without quotes. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern TYPE_NAME = Pattern.compile("[A-Z][A-Za-z0-9_]*");

    /** How the client reads a response body. */
    enum ResponseKind {
        /** Parsed as JSON into the return type. */
        JSON,
        /** A string return whose produced media types are all {@code text/*}. */
        TEXT,
        /** A binary return, such as a file download. */
        BLOB,
        /** No body is read. */
        VOID;

        static ResponseKind of(CodegenOperation op) {
            if (op.returnType == null) {
                return VOID;
            }
            if (op.isResponseFile || "Blob".equals(op.returnType)) {
                return BLOB;
            }
            if ("string".equals(op.returnType) && producesTextOnly(op)) {
                return TEXT;
            }
            return JSON;
        }

        /** The value of {@code responseType} in the generated request. */
        String literal() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Returns the arguments that pass a query parameter's style and explode to {@code appendQueryParam}, e.g.
     * {@code , 'deepObject', true}, {@code , 'json'} for a parameter declared with JSON content, or nothing for the
     * default, form with explode, and for content of another media type.
     *
     * @throws IllegalArgumentException if the style is not one OpenAPI allows in a query
     */
    static String queryStyleArguments(CodegenOperation op, CodegenParameter param) {
        if (param.contentType != null) {
            return jsonMediaType(param.contentType) != null ? ", 'json'" : "";
        }
        String style = param.style != null ? param.style : "form";
        if (!QUERY_STYLES.contains(style)) {
            throw new IllegalArgumentException("Query parameter " + param.baseName + " of operation " + op.operationId + " has style "
                    + style + ", which OpenAPI does not allow in a query");
        }
        return "form".equals(style) && param.isExplode ? "" : ", " + stringLiteral(style) + ", " + param.isExplode;
    }

    /**
     * Returns the first JSON media type of a declared content type, which OpenAPI lets list several types separated by
     * commas; {@code application/json} and {@code +json} types such as {@code application/vnd.api+json} count.
     */
    static String jsonMediaType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return Arrays.stream(contentType.split(",")).map(String::strip).filter(type -> JSON_MEDIA_TYPE.matcher(type).matches())
                .findFirst().orElse(null);
    }

    /** Tells whether an operation consumes JSON, or declares nothing and so leaves it to the default. */
    static boolean consumesJson(CodegenOperation op) {
        if (op.consumes == null || op.consumes.isEmpty()) {
            return true;
        }
        return op.consumes.stream().map(mediaType -> mediaType.get("mediaType")).anyMatch(type -> jsonMediaType(type) != null);
    }

    /**
     * Builds the template literal of a request path from the path as the spec writes it: every {@code {name}} becomes
     * an access to the parameter's key in {@code params}, URI-encoded unless the value is a number or a boolean.
     */
    static String buildPathLiteral(CodegenOperation op, Map<String, String> keysByParameter) {
        String path = op.path;
        if (op.pathParams.isEmpty()) {
            return stringLiteral(path);
        }
        for (CodegenParameter param : op.pathParams) {
            String access = "params." + keysByParameter.get(param.baseName);
            boolean plain = Boolean.TRUE.equals(param.isInteger) || Boolean.TRUE.equals(param.isNumber)
                    || Boolean.TRUE.equals(param.isBoolean) || "number".equals(param.dataType) || "boolean".equals(param.dataType);
            path = path.replace("{" + param.baseName + "}", "${" + (plain ? access : "encodeURIComponent(" + access + ")") + "}");
        }
        return "`" + path.replace("`", "\\`") + "`";
    }

    /** Collects the generated model names that a type expression refers to, such as {@code ReadonlyArray<Project>}. */
    static Set<String> referencedModels(String type, Set<String> knownModels) {
        Set<String> found = new HashSet<>();
        if (type == null) {
            return found;
        }
        // A string literal type such as 'Project' is no reference.
        Matcher matcher = TYPE_NAME.matcher(type.replaceAll("'(?:[^'\\\\]|\\\\.)*'", ""));
        while (matcher.find()) {
            if (knownModels.contains(matcher.group())) {
                found.add(matcher.group());
            }
        }
        return found;
    }

    /** Returns the preferred name, or the preferred name with the lowest digit suffix from 2 on, that is not yet in {@code used}, and adds it. */
    static String allocate(Set<String> used, String preferred) {
        String name = preferred;
        for (int suffix = 2; !used.add(name); suffix++) {
            name = preferred + suffix;
        }
        return name;
    }

    /** Tells whether an operation produces only text media types, such as {@code text/plain} or {@code text/csv}. */
    private static boolean producesTextOnly(CodegenOperation op) {
        if (op.produces == null || op.produces.isEmpty()) {
            return false;
        }
        for (Map<String, String> mediaType : op.produces) {
            String type = mediaType.get("mediaType");
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("text/")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the TypeScript property key for a JSON key. Interfaces describe the JSON exactly, so the key is used as
     * it is: reserved words such as {@code final} are valid property names and stay unescaped, and a key that is not an
     * identifier (e.g. {@code x-y}) is quoted.
     */
    static String toPropertyKey(String jsonKey) {
        return IDENTIFIER.matcher(jsonKey).matches() ? jsonKey : stringLiteral(jsonKey);
    }

    /** Returns a single-quoted TypeScript string literal for a string from the spec, such as a wire name. */
    static String stringLiteral(String value) {
        return "'" + new String(JsonStringEncoder.getInstance().quoteAsString(value)).replace("'", "\\'") + "'";
    }

    /** Splits a description into the lines of a doc comment, keeping {@code *}{@code /} from ending the comment early. */
    static List<String> docLines(String... texts) {
        return Arrays.stream(texts).filter(text -> text != null && !text.isBlank())
                .flatMap(text -> text.strip().lines())
                .map(line -> line.stripTrailing().replace("*/", "*\\/"))
                .toList();
    }

    private TypeScriptSnippets() {
    }
}
