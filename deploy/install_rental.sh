#!/bin/sh
set -eu

install -d -m 755 -o sarangit -g sarangit /opt/SRGH_IT/rental
install -m 644 -o sarangit -g sarangit /tmp/rental-20261003.jar /opt/SRGH_IT/rental/rental.jar
install -m 600 /etc/SRGH_IT/account.env /etc/SRGH_IT/rental.env
install -m 644 /tmp/SRGH_IT-rental.service /etc/systemd/system/SRGH_IT-rental.service
systemctl daemon-reload
systemctl enable --now SRGH_IT-rental.service
systemctl is-active SRGH_IT-rental.service
