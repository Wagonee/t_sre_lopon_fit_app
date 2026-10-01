FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests \
 && java -Djarmode=tools -jar target/lopon-backend.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:25-jre-noble
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="lopon-backend" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/Wagonee/t_sre_lopon_fit_app"
ENV APP_VERSION=${APP_VERSION} \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin lopon
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
