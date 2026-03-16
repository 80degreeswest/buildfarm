load("@rules_java//java:java_library.bzl", "java_library")

java_library(
    name = "plugins",
    srcs = glob(["*.java"]),
    plugins = ["//src/main/java/build/buildfarm/common:lombok"],
    visibility = ["//visibility:public"],
    deps = [
        "//src/main/java/build/buildfarm/common/config",
        "@buildfarm_maven//:org_pf4j_pf4j",
        "@buildfarm_maven//:org_projectlombok_lombok",
    ],
)
