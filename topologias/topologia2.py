#!/usr/bin/python

#   TCC - Abordagem para Distribuicao de Video Baseada em Redes Definidas por Software
#   Nadyan Suriel Pscheidt
#   Script de criacao de topologia Mininet
#   Topologia 2

import sys
from mininet.net import Mininet
from mininet.node import OVSSwitch, Controller, RemoteController
from mininet.topolib import TreeTopo
from mininet.log import setLogLevel, info
from mininet.cli import CLI

setLogLevel( 'info' )

def emptyNet(nsw = 1):

    net = Mininet(topo = None, build = False)

    info( '*** Init Experimento 2\n\n' )

    info( '*** Adding controller\n' )
    c0 = net.addController('c0', controller = RemoteController, ip = '127.0.0.1', port = 6653)

    info( '*** Adding hosts\n' )
    server1 = net.addHost( 'server1', ip = '10.0.0.1', mac = '00:00:00:00:00:01' )
    user1 = net.addHost( 'user1', ip = '10.0.0.2', mac = '00:00:00:00:00:02' )
    user2 = net.addHost( 'user2', ip = '10.0.0.3', mac = '00:00:00:00:00:03' )
    user3 = net.addHost( 'user3', ip = '10.0.0.4', mac = '00:00:00:00:00:04' )

    info( '*** Adding switch\n' )
    switch1 = net.addSwitch( 'switch1' )
    switch1.start([c0])

    info( '\n*** Creating links\n' )
    net.addLink(user1, switch1)
    net.addLink(user2, switch1)
    net.addLink(user3, switch1)
    net.addLink(server1, switch1)

    info( '*** Starting network\n')
    net.start()

    info( '*** Opening xterm\n')
    net.startTerms()
    info( '*** Running CLI\n' )
    CLI( net )

    info( '*** Stopping network' )
    net.stop()

if __name__ == '__main__':
    setLogLevel( 'info' )
    emptyNet()
