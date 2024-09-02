sudo docker stack rm appserver
mvn package
sudo docker build -t st104vm101.rtb-lab.pl/appserver:latest --build-arg="JAR_FILE=./target/project-bootstrap-1.0.jar" . 
sudo docker push st104vm101.rtb-lab.pl/appserver:latest
sudo docker stack deploy -c appserver.yaml appserver
