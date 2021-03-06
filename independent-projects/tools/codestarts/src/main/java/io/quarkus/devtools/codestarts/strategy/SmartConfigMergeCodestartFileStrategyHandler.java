package io.quarkus.devtools.codestarts.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.quarkus.devtools.codestarts.CodestartException;
import io.quarkus.devtools.codestarts.NestedMaps;
import io.quarkus.devtools.codestarts.reader.CodestartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class SmartConfigMergeCodestartFileStrategyHandler implements CodestartFileStrategyHandler {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory().configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false));

    @Override
    public String name() {
        return "smart-config-merge";
    }

    @Override
    public void process(Path targetDirectory, String relativePath, List<CodestartFile> codestartFiles, Map<String, Object> data)
            throws IOException {
        checkNotEmptyCodestartFiles(codestartFiles);

        final String configType = getConfigType(data);
        final Map<String, Object> config = new HashMap<>();
        for (CodestartFile codestartFile : codestartFiles) {
            final String content = codestartFile.getContent();
            if (!content.trim().isEmpty()) {
                final Map o = YAML_MAPPER.readerFor(Map.class).readValue(content);
                NestedMaps.deepMerge(config, o);
            }
        }
        final Path targetPath = targetDirectory.resolve(relativePath);
        createDirectories(targetPath);
        if (Objects.equals(configType, "config-properties")) {
            writePropertiesConfig(targetPath, config);
            return;
        }
        if (Objects.equals(configType, "config-yaml")) {
            writeYamlConfig(targetPath, config);
            return;
        }
        throw new CodestartException("Unsupported config type: " + configType);
    }

    private void writeYamlConfig(Path targetPath, Map<String, Object> config) throws IOException {
        checkTargetDoesNotExist(targetPath);
        YAML_MAPPER.writerFor(Map.class).writeValue(targetPath.toFile(), config);
    }

    private void writePropertiesConfig(Path targetPath, Map<String, Object> config) throws IOException {
        final StringBuilder builder = new StringBuilder();
        final HashMap<String, String> flat = new HashMap<>();
        flatten("", flat, config);
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            builder.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        final Path propertiesTargetPath = targetPath.getParent()
                .resolve(targetPath.getFileName().toString().replace(".yml", ".properties"));
        checkTargetDoesNotExist(propertiesTargetPath);
        Files.write(propertiesTargetPath, builder.toString().getBytes());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void flatten(String prefix, Map<String, String> target, Map<String, ?> map) {
        for (Map.Entry entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                flatten(entry.getKey() + ".", target, (Map) entry.getValue());
            } else {
                // TODO: handle different types of values
                target.put(prefix + entry.getKey(), entry.getValue().toString());
            }
        }
    }

    private static String getConfigType(Map<String, Object> data) {
        final Optional<String> config = NestedMaps.getValue(data, "codestart-project.config.name");
        return config.orElseThrow(() -> new CodestartException("Config type is required"));
    }

}
