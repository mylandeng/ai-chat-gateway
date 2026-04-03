FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/ai-chat-gateway-1.0.0.jar app.jar

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", \
    "--spring.profiles.active=prod"]
