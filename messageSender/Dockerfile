FROM openjdk:17-jdk
RUN mkdir -p /home/app/
ADD target/MessageSender.jar /home/app/MessageSender.jar
CMD ["java","-jar","/home/app/MessageSender.jar"]