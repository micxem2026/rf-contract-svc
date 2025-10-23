FROM amazoncorretto:17.0.13-al2023-headful as builder

# Копируем сертификат во временную папку внутри контейнера
COPY gitlab.micxem.crt /tmp/gitlab.micxem.crt

# Используем утилиту 'keytool' из состава JDK, чтобы добавить наш сертификат
# в стандартное хранилище доверия Java (cacerts).
RUN keytool -importcert -alias gitlab.micxem -file /tmp/gitlab.micxem.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -noprompt

# Удаляем временный файл сертификата (хорошая практика)
RUN rm /tmp/gitlab.micxem.crt

WORKDIR /src
COPY . .
# Копируем кеш Gradle wrapper дистрибутива
COPY --chown=root:root .gradle /root/.gradle

SHELL ["/bin/bash", "-c"]

# Установка xargs
RUN dnf install -y findutils

# Объявляем аргумент, который мы будем получать из команды docker build
ARG CI_JOB_TOKEN

# Собираем проект
RUN chmod +x ./gradlew && ./gradlew --no-daemon assemble -Pgitlab.registry.token=$CI_JOB_TOKEN

FROM gitlab.micxem:5050/rights-flow/rf-base-images/liberica-openjdk:17.0.13-cds
COPY --from=builder /src/rf-contract-app/build/libs/rf-contract-svc.jar rf-contract-svc.jar
ENTRYPOINT ["java","-XX:+UseContainerSupport","-Xms256m","-Xmx512m","-jar","/rf-contract-svc.jar"]