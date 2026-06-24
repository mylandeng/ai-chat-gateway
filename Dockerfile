# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
# 先下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# 第二阶段：运行
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/ai-chat-gateway-1.0.0.jar app.jar

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", \
    "--spring.profiles.active=prod"]
