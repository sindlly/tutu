# syntax=docker/dockerfile:1
# 多阶段构建：mvn 在容器里跑，不依赖宿主机的 maven 与 JDK 版本。
# 依赖层走 BuildKit 缓存挂载，改代码重新构建只编译，不重复下依赖。

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# 只拷 pom 先解析依赖，pom 不变时这层直接命中缓存
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# 健康检查要用；顺手清掉 apt 缓存控制体积
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/homework-grader-0.0.1-SNAPSHOT.jar app.jar
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
