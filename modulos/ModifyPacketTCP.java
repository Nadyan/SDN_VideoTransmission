/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.aggregator;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;

public class ModifyPacketTCP implements IFloodlightModule, IOFMessageListener {

    /*
     * UDESC - Universidade do Estado de Santa Catarina
     * Bacharelado em Ciência da Computação
     * Abordagem para Distribuição de Vídeo Baseada em Redes Definidas por Software
     * Nadyan Suriel Pscheidt
     * 
     * Módulo responsável pela modificação do cabeçalho dos pacotes de vídeo
     * com origem no servidor e destino nos usuários.
     * Tem como objetivo duplicar o pacote e modificar o cabeçalho de um deles
     * (principalmente a porta TCP) para enviar ao segundo usuário.
     * 
     * Deve ser executado após o módulo AggregatorTCP.
     */
    
    private IFloodlightProviderService floodlightProvider;
    private static Logger logger;
    
    private boolean sequenciaInit = false;
    private boolean dupGoodToGo = false;
    private int sequencia;
    
    @Override
    public String getName() {
        return ModifyPacketTCP.class.getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        
        /* Deve ser executado após o AggregatorTCP */
        
        if(type.equals(OFType.PACKET_IN) && name.equals("aggregatortcp")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        
        /* Deve ser executado antes do Forwarding */
        
        if(type.equals(OFType.PACKET_IN) && name.equals("forwarding")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(AggregatorTCP.class); 
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        
        /* Duplica o pacote de video e modifica o cabeçalho do clone
         * com as informações do segundo usuário */
        
        /* TODO: FAZER A VERIFICACAO DE QUE O CLIENTE RESPONDEU COM O ACK O PARTIAL CONTENT
         * DEPOIS QUE O CLIENTE RESPONDER SET A FLAG dupGoodToGo COMO TRUE E COMECA A DUPLICACAO
         *  */
        
        if (AggregatorTCP.headerInfo.getFlag() && dupGoodToGo == false) {   // mudar para duGoodToGo == true

            if (sequenciaInit == false && AggregatorTCP.copyPartial == true) {
                /* Inicio do Sequence Number */
                
                sequencia = AggregatorTCP.partialSeq + AggregatorTCP.partialLen + 1;    // inicio sequencia + tamanho do pacote partial + 1
                sequenciaInit = true;   // entra apenas na primeira vez
            }
            
            if (sequenciaInit == true) {
                Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
                
                MacAddress srcMac = eth.getSourceMACAddress();
                MacAddress dstMac = eth.getDestinationMACAddress();
                
                if (dstMac.equals(AggregatorTCP.originalUserInfo.getClientMac())
                    && srcMac.equals(AggregatorTCP.headerInfo.getServerMac())) {
                    
                    /* Se for um pacote com destino ao primeiro usuario que requisitou o conteudo */
                    
                    if (eth.getEtherType() == EthType.IPv4) {
                        
                        IPv4 ipv4 = (IPv4) eth.getPayload();
                        
                        IPv4Address srcIp = ipv4.getSourceAddress();
                        IPv4Address dstIp = ipv4.getDestinationAddress();
                        
                        if (dstIp.equals(AggregatorTCP.originalUserInfo.getClientIp())
                            && srcIp.equals(AggregatorTCP.headerInfo.getServerIp())) {
                            
                            if (ipv4.getProtocol() == IpProtocol.TCP) {
                                
                                TCP tcp = (TCP) ipv4.getPayload();
                                
                                if (tcp.getDestinationPort().equals(AggregatorTCP.originalUserInfo.getClientPort())    // Origem servidor
                                        && (tcp.getFlags() == (short) 0x018 || tcp.getFlags() == (short) 0x010)) {     // [PSH, ACK] ou [ACK]
                                    
                                    /* Se for um pacote de video [PSH, ACK] vindo do servidor */
                                    
                                    /* Duplica o pacote nas tres camadas */
                                    Ethernet eth2 = (Ethernet) eth.clone(); // eth2 terá o cabeçalho alterado para encaminhamento ao segundo usuário
                                    IPv4 ipv42 = (IPv4) eth2.getPayload();
                                    TCP tcp2 = (TCP) ipv42.getPayload();
                                    
                                    /* Modifica os cabecalhos nas tres camadas */
                                    eth2.setDestinationMACAddress(AggregatorTCP.headerInfo.getClientMac());
                                    ipv42.setDestinationAddress(AggregatorTCP.headerInfo.getClientIp());
                                    tcp2.setDestinationPort(AggregatorTCP.headerInfo.getClientPort());
                                    tcp2.setSequence(sequencia);  // muda a cada pacote enviado de acordo com o calculo
                                    tcp2.setAcknowledge(AggregatorTCP.headerInfo.getAck()); // sempre o mesmo
                                   
                                    /* Remonta o pacote */
                                    eth2.setPayload(ipv42);
                                    ipv4.setPayload(tcp2);
                                    byte[] serializedData = eth2.serialize();
                                    
                                    /* Cria o Packet-out com cabeçalho modificado */
                                    OFPacketOut po = sw.getOFFactory().buildPacketOut()
                                                       .setData(serializedData)
                                                       .setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().output(OFPort.NORMAL, AggregatorTCP.headerInfo.getSwitchPort())))
                                                       .setInPort(OFPort.CONTROLLER)
                                                       .build();
                                    
                                    /* Escreve o Packet-out no switch */
                                    sw.write(po);
                                    /*  Calculo do proximo numero de sequencia */
                                    int tcpLen = (int) ipv4.getTotalLength() - (int) ipv4.getHeaderLength() - 32;   // 32 = tcp header length
                                    sequencia += tcpLen;
                                    logger.info("Pacote duplicado, seq: " + sequencia);
                                }
                            }
                        }
                    }
                }
            }
        }
                
        return Command.CONTINUE;
    }
}

