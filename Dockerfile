FROM gcr.io/distroless/java21-debian12
WORKDIR /app
COPY build/libs/*.jar /app/app.jar
USER nonroot:nonroot
ENTRYPOINT ["java","-jar","/app/app.jar"]
