sudo docker build -t st104vm101.rtb-lab.pl/loadbalancer:latest .
sudo docker push st104vm101.rtb-lab.pl/loadbalancer:latest
sudo docker-compose up -d -c loadbalancer.yaml loadbalancer
