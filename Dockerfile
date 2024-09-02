FROM openjdk:17.0.2-slim-buster

RUN groupadd -g 10240 worker && \
    useradd -r -u 10240 -g worker worker

USER worker:worker

ARG JAR_FILE

ADD ${JAR_FILE} /app/app.jar

COPY . /usr/src/myapp
WORKDIR /usr/src/myapp

ENTRYPOINT java \
    -Xmx2g \
    -jar /app/app.jar