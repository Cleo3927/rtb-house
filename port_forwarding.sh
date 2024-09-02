# https://serverfault.com/questions/997124/ssh-r-binds-to-127-0-0-1-only-on-remote
ssh -R 8089:localhost:8088 rtb
ssh -g -L 8090:localhost:8089 st104vm101.rtb-lab.pl

# use st104vm101.rtb-lab.pl:8090 to connect