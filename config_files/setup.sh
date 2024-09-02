sudo apt -y install ansible sshpass maven

ANSIBLE_ARGS="ansible_user=st104 ansible_password=tpv43bzm ansible_ssh_extra_args='-o StrictHostKeyChecking=no'"
ansible-playbook --extra-vars "$ANSIBLE_ARGS" -i hosts docker-playbook.yaml
ansible-playbook --extra-vars "$ANSIBLE_ARGS" -i hosts registry-playbook.yaml
ansible-playbook --extra-vars "$ANSIBLE_ARGS" -i hosts swarm-playbook.yaml
ansible-playbook --extra-vars "$ANSIBLE_ARGS" -i hosts aerospike-playbook.yaml