package ai.intentchain.core.prompt;

import ai.intentchain.core.utils.JinjaTemplateUtil;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;

import java.util.Map;

public class JinjaPromptTemplateFactory implements PromptTemplateFactory {

    @Override
    public Template create(Input input) {
        return new JinjaTemplate(input.getTemplate());
    }

    static class JinjaTemplate implements Template {

        private final String template;

        public JinjaTemplate(String template) {
            this.template = ValidationUtils.ensureNotBlank(template, "template");
        }

        @Override
        public String render(Map<String, Object> variables) {
            return JinjaTemplateUtil.render(template, variables);
        }
    }
}
