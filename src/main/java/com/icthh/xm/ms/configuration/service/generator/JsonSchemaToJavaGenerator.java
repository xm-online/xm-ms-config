package com.icthh.xm.ms.configuration.service.generator;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.NoopAnnotator;
import org.jsonschema2pojo.SchemaGenerator;
import org.jsonschema2pojo.SchemaMapper;
import org.jsonschema2pojo.SchemaStore;
import org.jsonschema2pojo.rules.RuleFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaToJavaGenerator {

    public String convertSchemaToJava(GenerationConfig config, String jsonSchema, String className, String packageName) {
        JCodeModel codeModel = new JCodeModel();

        SchemaMapper mapper = new SchemaMapper(
                new RuleFactory(config, new NoopAnnotator(), new SchemaStore()),
                new SchemaGenerator()
        );

        try {
            mapper.generate(codeModel, className, packageName, jsonSchema);

            StringWriter writer = new StringWriter();
            codeModel.build(new InMemoryCodeWriter(writer));

            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static class InMemoryCodeWriter extends CodeWriter {
        private final Writer writer;

        public InMemoryCodeWriter(Writer writer) {
            this.writer = writer;
        }

        @Override
        public OutputStream openBinary(JPackage pkg, String fileName) {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    writer.write(toString(StandardCharsets.UTF_8));
                    writer.flush();
                }
            };
        }

        @Override
        public void close() {}
    }
}
