FROM eclipse-temurin:17.0.5_8-jre-alpine
RUN mkdir -p /home/app/
ADD target/MessageSender.jar /home/app/MessageSender.jar
CMD ["java","-jar","/home/app/MessageSender.jar"]