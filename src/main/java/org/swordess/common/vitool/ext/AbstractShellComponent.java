package org.swordess.common.vitool.ext;

import org.springframework.shell.component.StringInput;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class AbstractShellComponent extends org.springframework.shell.standard.AbstractShellComponent {

    public class Option {

        private final List<Supplier<String>> providers;

        private Option() {
            this(new ArrayList<>());
        }

        private Option(List<Supplier<String>> providers) {
            this.providers = providers;
        }

        Option or(Supplier<String> provider) {
            providers.add(provider);
            return this;
        }

        public Option orEnv(String envVarName) {
            return or(() -> System.getenv(envVarName));
        }

        public Option orInput(String prompt) {
            return orInput(prompt, null, true);
        }

        public Option orInput(String prompt, String defaultValue) {
            return orInput(prompt, defaultValue, true);
        }

        public Option orInput(String prompt, String defaultValue, boolean mask) {
            return or(() -> {
                StringInput component = new StringInput(getTerminal(), prompt, defaultValue);
                component.setResourceLoader(getResourceLoader());
                component.setTemplateExecutor(getTemplateExecutor());
                if (mask) {
                    component.setMaskCharater('*');
                }

                StringInput.StringInputContext context = component.run(StringInput.StringInputContext.empty());
                return context.getResultValue();
            });
        }

        public Option must(String errMsg) {
            return or(() -> {
                throw new IllegalArgumentException(errMsg);
            });
        }

        public String get() {
            for (Supplier<String> provider : providers) {
                String value = provider.get();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
        }

    }

    public Option optionValue(String value) {
        return new Option().or(() -> value);
    }

}
