ARG GITLAB
FROM amazoncorretto:17.0.13-al2023-headful as builder

# 1. Объявляем аргумент, который придет из docker build
ARG CERT_NAME

# Проверка, что аргумент не пустой (опционально, но полезно для отладки)
RUN if [ -z "$CERT_NAME" ]; then echo "ERROR: CERT_NAME is not defined"; exit 1; fi

# 2. Копируем сертификат во временную папку внутри контейнера, используя имя из аргумента
COPY ${CERT_NAME} /tmp/${CERT_NAME}

# 3. Используем утилиту 'keytool' из состава JDK, чтобы добавить наш сертификат
#    в стандартное хранилище доверия Java (cacerts).
RUN keytool -importcert -alias gitlab.cert -file /tmp/${CERT_NAME} -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -noprompt

# 4. Удаляем временный файл
RUN rm /tmp/${CERT_NAME}

WORKDIR /src
COPY . .
# Копируем кеш Gradle wrapper дистрибутива
COPY --chown=root:root .gradle /root/.gradle

SHELL ["/bin/bash", "-c"]

# Диагностика сети
RUN echo "=== Network diagnostics ===" && cat /etc/resolv.conf && curl -I https://cdn.amazonlinux.com/ || true

# Настройка DNF и установка findutils с retry
RUN echo "timeout=180" >> /etc/dnf/dnf.conf && \
    echo "retries=10" >> /etc/dnf/dnf.conf && \
    dnf clean all && \
    for i in 1 2 3 4 5; do \
      dnf install -y findutils && break || \
      (echo "Attempt $i failed, retrying in 10 seconds..." && sleep 10); \
    done

#RUN dnf install -y findutils

# Объявляем аргумент, который мы будем получать из команды docker build
ARG CI_JOB_TOKEN
ARG GITLAB_REG_URL
ARG GITLAB_CACHE_REG_URL

# Собираем проект
RUN chmod +x ./gradlew && ./gradlew --no-daemon assemble -Pgitlab.registry.token=$CI_JOB_TOKEN -Pgitlab.registry.url=$GITLAB_REG_URL -Pgitlab.cache.registry.url=$GITLAB_CACHE_REG_URL

FROM ${GITLAB}/rights-flow/rf-base-images/liberica-openjdk:17.0.13-cds
COPY --from=builder /src/rf-contract-app/build/libs/rf-contract-svc.jar rf-contract-svc.jar
ENTRYPOINT ["java","-XX:+UseContainerSupport","-Xms512m","-Xmx1024m","-jar","/rf-contract-svc.jar"]