package com.zebrunner.jenkins.pipeline

import com.zebrunner.jenkins.Logger

public class Configuration {

    private def context

    public final static def mustOverride = "{must_override}"

    public final static def CREDS_MAVEN_SETTINGS = "maven"
    
    public final static def AGENT_ENV = "agent.env"
    public final static def VARIABLES_ENV = "variables.env"
    
    public final static def CREDS_CUSTOM_PIPELINE = "jenkinsfile"

    private static final String CAPABILITIES = "capabilities"

    private static final String ZEBRUNNER_VERSION = "ZEBRUNNER_VERSION"
    private static final String ZEBRUNNER_PIPELINE = "ZEBRUNNER_PIPELINE"

    private static final String ADMIN_EMAILS = "ADMIN_EMAILS"

    //list of CI job params as a map
    protected static Map params = [:]
    //list of required goals vars which must present in command line obligatory
    protected static Map vars = [:]

    // example of the logging for static @NonCPS calls
    //private static String del = ""

    public Configuration(context) {
        this.context = context
        this.loadContext()
    }

    @NonCPS
    public static Map getParams() {
        return params
    }

    @NonCPS
    public static Map getVars() {
        return vars
    }

    public enum Parameter {

        //vars
        JOB_MAX_RUN_TIME("JOB_MAX_RUN_TIME", "60"),

        INFRA_HOST("INFRA_HOST", "demo.qaprosoft.com"),

        SELENIUM_URL("SELENIUM_URL", mustOverride, true),

        JOB_URL("JOB_URL", mustOverride),
        JOB_NAME("JOB_NAME", mustOverride),
        JOB_BASE_NAME("JOB_BASE_NAME", mustOverride),
        BUILD_NUMBER("BUILD_NUMBER", mustOverride),

        TIMEZONE("user.timezone", "UTC"),

        BROWSERSTACK_ACCESS_KEY("BROWSERSTACK_ACCESS_KEY", "\${BROWSERSTACK_ACCESS_KEY}", true),

        DOCKER_HUB_USERNAME("DOCKER_HUB_USERNAME", mustOverride),
        DOCKER_HUB_PASSWORD("DOCKER_HUB_PASSWORD", mustOverride)

        private final String key
        private final String value
        private final Boolean isSecured

        Parameter(String key, String value) {
            this(key, value, false)
        }

        Parameter(String key, String value, Boolean isSecured) {
            this.key = key
            this.value = value
            this.isSecured = isSecured
        }

        @NonCPS
        public String getKey() {
            return key
        }

        @NonCPS
        public String getValue() {
            return value
        }

        @NonCPS
        public Boolean isSecured() {
            return isSecured
        }
    }

    public String getGlobalProperty(String name) {
        return context.env.getEnvironment().get(name)
    }

    @NonCPS
    public void loadContext() {
        // 1. load all obligatory Parameter(s) and their default key/values to vars.
        // any non empty value should be resolved in such order: Parameter, envvars and jobParams

        def enumValues = Parameter.values()
        for (enumValue in enumValues) {
            //a. set default values from enum
            vars.put(enumValue.getKey(), enumValue.getValue())
        }

        //b. redefine values from global variables if any
        def envVars = context.env.getEnvironment()
        for (var in vars) {
            if (envVars.get(var.getKey()) != null) {
                vars.put(var.getKey(), envVars.get(var.getKey()))
            }
        }

        // 2. Load all job parameters into unmodifiable map
        def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
        for (param in jobParams) {
            if (param.value != null) {
                putParamCaseInsensitive(param.name, param.value)
            }
        }

        //3. Replace vars and/or params with capabilities prefix
        parseValues(params.get(CAPABILITIES), ";", CAPABILITIES)

        //TODO: wrap 3a and 3b into the single method or remove after fixing cron matrix
        //3.a to support old "browser" capability as parameter
        if (params.get("browser") != null) {
            if (!params.get("browser").isEmpty() && !params.get("browser").equalsIgnoreCase("NULL")) {
                putParamCaseInsensitive("capabilities.browserName", params.get("browser"))
            }
        }
        //3.b to support old "browser_version" capability as parameter
        if (params.get("browser_version") != null) {
            if (!params.get("browser_version").isEmpty() && !params.get("browser_version").equalsIgnoreCase("NULL")) {
                putParamCaseInsensitive("capabilities.browserVersion", params.get("browser_version"))
            }
        }

        //4. Replace vars and/or params with zafiraFields values
        parseValues(params.get("zafiraFields"))
        //5. Replace vars and/or params with overrideFields values
        parseValues(params.get("overrideFields"))

        def securedParameters = []
        for (enumValue in enumValues) {
            if (enumValue.isSecured()) {
                securedParameters << enumValue.getKey()
            }
        }

        context.println("VARS:")
        for (var in vars) {
            if (var.getKey() in securedParameters) {
                context.println(var.getKey() + "=********")
            } else {
                context.println(var)
            }
        }

        context.println("PARAMS:")
        for (param in params) {
            context.println(param)
        }

        //6. TODO: investigate how private pipeline can override those values
        // public static void set(Map args) - ???
    }

    @NonCPS
    private static void parseValues(values) {
        parseValues(values, ",")
    }

    @NonCPS
    private static void parseValues(values, separator) {
        parseValues(values, separator, "")
    }

    @NonCPS
    private static void parseValues(values, separator, keyPrefix) {
        if (values) {
            for (value in values.split(separator)) {
                def keyValueArray = value.trim().split("=")
                if (keyValueArray.size() > 1) {
                    def parameterName = keyValueArray[0]
                    def parameterValue = keyValueArray[1]
                    if (keyPrefix.isEmpty()) {
                        putParamCaseInsensitive(parameterName, parameterValue)
                    } else {
                        putParamCaseInsensitive(keyPrefix + "." + parameterName, parameterValue)
                    }
                }
            }
        }
    }

    @NonCPS
    private static void putParamCaseInsensitive(parameterName, parameterValue) {
        if (vars.get(parameterName)) {
            vars.put(parameterName, parameterValue)
            //del += "varName: ${parameterName}; varValue: ${parameterValue}\n"
        } else if (vars.get(parameterName.toUpperCase())) {
            vars.put(parameterName.toUpperCase(), parameterValue)
            //del += "varName: ${parameterName}; varValue: ${parameterValue}\n"
        } else {
            params.put(parameterName, parameterValue)
            //del += "paramName: ${parameterName}; paramValue: ${parameterValue}\n"
        }
    }

    @NonCPS
    public static String get(Parameter param) {
        return get(param.getKey())
    }

    @NonCPS
    public static String get(String paramName) {
        if (params.get(paramName) != null) {
            return params.get(paramName)
        } else if (vars.get(paramName) != null) {
            return vars.get(paramName)
        } else {
            return ""
        }
    }

    public static void set(Parameter param, String value) {
        set(param.getKey(), value)
    }

    public static void set(String paramName, String value) {
        // explicit setter should override in params as it has the highest priority order
        params.put(paramName, value)
    }

    // simple way to reload as a bundle all project custom arguments from private pipeline
    public static void set(Map args) {
        for (arg in args) {
            vars.put(arg.getKey(), arg.getValue())
        }
    }

    /*
     * replace all ${PARAM} occurrences by real values from var/params
     * String cmd
     * return String cmd
     */

    @NonCPS
    public static String resolveVars(String cmd) {
        return cmd.replaceAll('\\$\\{[^\\{\\}]*\\}') { m -> get(m.substring(2, m.size() - 1)) }
    }

    public static void remove(String key) {
        vars.remove(key)
        params.remove(key)
    }

}
