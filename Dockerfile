# Используем образ с Java 24, раз она у тебя в логах
FROM eclipse-temurin:24-jdk-alpine

WORKDIR /app

# Копируем собранный jar-файл
COPY target/*.jar app.jar

# Открываем стандартный порт Спринга
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]