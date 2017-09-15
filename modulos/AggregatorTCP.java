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
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
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
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.HTTP;
import net.floodlightcontroller.packet.HTTPMethod;
import net.floodlightcontroller.util.FlowModUtils;

public class AggregatorTCP implements IFloodlightModule, IOFMessageListener {
    
    /*
     * UDESC - Universidade do Estado de Santa Catarina
     * Bacharelado em Ciência da Computação
     * Abordagem para Distribuição de Vídeo Baseada em Redes Definidas por Software
     * Nadyan Suriel Pscheidt
     * 
     */

    private IFloodlightProviderService floodlightProvider;
    private static Logger logger;
    public static final TransportPort HTTP_PORT = TransportPort.of(5001);
    public boolean repeat = false;
    
    /*  Listas de requisições */
    protected List<Request> requests;
    protected List<String> videosInTraffic;
    protected List<Request> gets;
    
    /* Endereços */
    //private IPv4Address ipServer1 = IPv4Address.of("10.0.0.1");
    private IPv4Address ipUser1 = IPv4Address.of("10.0.0.2");
    private IPv4Address ipUser2 = IPv4Address.of("10.0.0.3");
    private IPv4Address ipUser3 = IPv4Address.of("10.0.0.4");
    //private MacAddress macServer1 = MacAddress.of("00:00:00:00:00:01");
    //private MacAddress macUser1 = MacAddress.of("00:00:00:00:00:02");
    //private MacAddress macUser2 = MacAddress.of("00:00:00:00:00:03");
    //private MacAddress macUser3 = MacAddress.of("00:00:00:00:00:04");
    
    /* Portas */
    private int s1Port = 4;
    private int u1Port = 1;
    private int u2Port = 2;
    private int u3Port = 3;
    private int userPort = -1;
    
    @Override
    public String getName() {
        return AggregatorTCP.class.getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
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
        
        /* Criação das listas */
        requests = new ArrayList<Request>();                        // Lista com as informações dos clientes
        videosInTraffic = new ArrayList<String>();                  // Lista com os videos trafegando
        gets = new ArrayList<Request>();                            // Lista para administrar os gets
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        MacAddress srcMac = eth.getSourceMACAddress();              // MAC cliente
        MacAddress dstMac = eth.getDestinationMACAddress();         // MAC server
        
        if(eth.getEtherType() == EthType.IPv4) {
            
            /* Pacote IPv4 */
            
            IPv4 ipv4 = (IPv4) eth.getPayload();
            
            IPv4Address srcIp = ipv4.getSourceAddress();            // IP cliente
            IPv4Address dstIp = ipv4.getDestinationAddress();       // IP server
            
            if(srcIp.equals(ipUser1) == true) {                     // Porta do Switch para cada usuário
                userPort = u1Port;
            } else if(srcIp.equals(ipUser2) == true) {
                userPort = u2Port;
            } else if(srcIp.equals(ipUser3) == true) {
                userPort = u3Port;
            }
            
            if(ipv4.getProtocol() == IpProtocol.TCP) {
                
                /* Pacote TCP */
                
                TCP tcp = (TCP) ipv4.getPayload();

                /* Pega header HTTP do payload do TCP */
                Data dt = (Data)tcp.getPayload();
                byte[] txt = dt.getData();
                String headerHttp = new String(txt);
                
                /* Divisão do header em 3 partes: Método, URI e resto */
                String arr[] = headerHttp.split(" ", 3);
                String method = arr[0];
                String videoId;
                
                HTTP http = new HTTP();
                
                /* Identificação do método da requisição HTTP */
                if(method.compareTo("HEAD") == 0) {
                    http.setHTTPMethod(HTTPMethod.HEAD);
                } else if(method.compareTo("GET") == 0) {
                    http.setHTTPMethod(HTTPMethod.GET);
                } else {
                    http.setHTTPMethod(HTTPMethod.NONE);
                }
                
                if(http.getHTTPMethod() == HTTPMethod.HEAD) {
                    
                    /* Pacote HTTP com método HEAD, informando o vídeo que será requisitado */
                    
                    /* Adiciona na lista o video que será requisitado
                     * para comparar com o GET posteriormente 
                     */
                    
                    /* Pega apenas o id do video do URI */
                    videoId = arr[1].substring(arr[1].lastIndexOf("/") + 1);
                    
                    logger.info("---------- HEAD recebido ----------");
                    logger.info("URI: " + arr[1]);
                    logger.info("VideoId: " + videoId);
                    
                    Request novoHead = new Request(videoId, userPort);
                    gets.add(novoHead);
                    
                    return Command.CONTINUE;
                    
                } else if(http.getHTTPMethod() == HTTPMethod.GET && gets.isEmpty() == false) {
                   
                    /* Se for um GET de um HEAD anterior */
                    
                    /* Pacote HTTP com método GET, requisitando o vídeo.
                     * É necessário pegar a requisição pelo GET pois a porta
                     * que será enviado o vídeo é a porta na qual saiu o GET, 
                     * por isso, não é possível analisar a requisição pelo HEAD.
                     */
                    
                    TransportPort srcPort = tcp.getSourcePort();    // porta cliente de origem do get
                    TransportPort tcpTranspPort = srcPort;          // porta do cliente que será enviado o video
                    
                    String uri = arr[1];
                    videoId = arr[1].substring(arr[1].lastIndexOf("/") + 1);
                    //String resto = arr[2];
                    
                    if(requests.isEmpty() == false && requests.get(0).getPort() == userPort 
                                                   && requests.get(0).getTcpPort() != tcpTranspPort) {
                        
                        /* SE FOR UM GET DE RECONEXAO, ATUALIZA A PORTA TCP DO REQUEST */
                        /* Melhorar isso, da pra fazer uma lista que armazena todos os requests,
                         * e pra montar o fluxo pega o "mais recente" */
                        
                        Request atualiza = new Request(requests.get(0).getVideoId(), 
                                                       requests.get(0).getMacAddress(),
                                                       requests.get(0).getIpAddress(),
                                                       requests.get(0).getPort(),
                                                       tcpTranspPort);
                        
                        requests.set(0, atualiza);
                        logger.info("Porta TCP atualizada para o usuario " + requests.get(0).getIpAddress() + "!");
                    }

                    if(gets.get(0).getVideoId().equals(videoId)) {
                        
                        /* Se for o GET correspondente ao HEAD */
                            
                        logger.info("--- GET correspondente recebido ---");
                        //logger.info("Method: " + method);
                        logger.info("URI: " + uri);
                        logger.info("VideoId: " + videoId);
                        
                        if(videosInTraffic.contains(videoId) == false) {
                            
                            /* Se ainda havia uma transferência do vídeo requisitado */
                
                            Request novoRequest = new Request(videoId, srcMac, srcIp, userPort, tcpTranspPort);
                            
                            videosInTraffic.add(videoId);
                            requests.add(novoRequest);
                            logger.info("Requisição nova " + videoId + " de " + srcIp);
                            
                            return Command.CONTINUE;
                            
                        } else {
                            
                            /*  Se o vídeo requisitado já estava sendo transmitido, agrega os dois fluxos
                             *  - Primeiro usuário: Usuário que requisitou o vídeo inicialmente; e
                             *  - Segundo usuário: Usuário que requisitou o vídeo quando o mesmo já estava sendo transmitido.
                             */
                            
                            int index = -1;
                            Request searchKey = new Request(videoId);
                            index = Collections.binarySearch(requests, searchKey, new Comparador());    // Procura a posição na lista do vídeo
                            if(index == -1) {
                                logger.info("ERRO: ID de vídeo não encontrado");
                            }
                            IPv4Address originalIp = requests.get(index).getIpAddress();                // Recupera IP do primeiro usuário
                            MacAddress originalMac = requests.get(index).getMacAddress();               // Recupera MAC do primeiro usuário
                            int originalPort = requests.get(index).getPort();                           // Recupera Porta do primeiro usuário
                            TransportPort originalTcpTranspPort = requests.get(index).getTcpPort();     // Recupera Porta TCP do primeiro usuario

                            if(originalIp.equals(srcIp) == false && repeat == false) {
    
                                repeat = true;
                                
                                /* Inicio da montagem dos fluxos */
                                OFFactory my13Factory = OFFactories.getFactory(OFVersion.OF_13);
                                OFActions actions = my13Factory.actions();
                                OFOxms oxms = my13Factory.oxms();
                                
                                List<OFAction> actionsFrom = new ArrayList<OFAction>();                  // Lista de actions para o fluxo do server para o cliente
                                List<OFAction> actionsTo = new ArrayList<OFAction>();                    // Lista de actions para o fluxo do cliente para o server (ACK)
                                
                                /* Montagem do fluxo server -> cliente */
                                /* Set do IP do segundo usuário */
                                OFActionSetField setDstIpC1 = actions.buildSetField()
                                                             .setField(oxms.buildIpv4Dst()
                                                             .setValue(srcIp)
                                                             .build()).build();
                                
                                /* Set do MAC do segundo usuário */
                                OFActionSetField setDstMacC1 = actions.buildSetField()
                                                              .setField(oxms.buildEthDst()
                                                              .setValue(srcMac)
                                                              .build()).build();
                                
                                /* Set do IP do primeiro usuário */
                                OFActionSetField setDstIpC2 = actions.buildSetField()
                                                             .setField(oxms.buildIpv4Dst()
                                                             .setValue(originalIp)
                                                             .build()).build();
                                
                                /* Set do MAC do primeiro usuário */
                                OFActionSetField setDstMacC2 = actions.buildSetField()
                                                              .setField(oxms.buildEthDst()
                                                              .setValue(originalMac)
                                                              .build()).build();
        
                                /* Set das portas de saída do switch para os usuários */
                                OFActionOutput outputClient1 = actions.output(OFPort.of(userPort), Integer.MAX_VALUE);      // Porta do segundo usuário
                                OFActionOutput outputClient2 = actions.output(OFPort.of(originalPort), Integer.MAX_VALUE);  // Porta do primeiro usuário
                                
                                actionsFrom.add(setDstIpC1);
                                actionsFrom.add(setDstMacC1);
                                actionsFrom.add(outputClient1);
                                actionsFrom.add(setDstIpC2);
                                actionsFrom.add(setDstMacC2);
                                actionsFrom.add(outputClient2);
                                
                                OFFlowAdd flowFromTCP = fluxoTCP(createMatchFromPacket(sw, originalTcpTranspPort, cntx, originalIp, dstIp), my13Factory, actionsFrom);
                                
                                /* Montagem do fluxo cliente -> server (ACK), apenas o primeiro usuario responde */
                                /* Set do IP do server */
                                OFActionSetField setDstIpS1 = actions.buildSetField()
                                                             .setField(oxms.buildIpv4Dst()
                                                             .setValue(dstIp)    // IP do server
                                                             .build()).build();
                                
                                /* Set do MAC do server */
                                OFActionSetField setDstMacS1 = actions.buildSetField()
                                                              .setField(oxms.buildEthDst()
                                                              .setValue(dstMac)
                                                              .build()).build();
                                
                                /* Set da porta de saída do switch para o server */
                                OFActionOutput outputServer1 = actions.output(OFPort.of(s1Port), Integer.MAX_VALUE);
                                
                                actionsTo.add(setDstIpS1);
                                actionsTo.add(setDstMacS1);
                                actionsTo.add(outputServer1);
                                                            
                                OFFlowAdd flowToTCP = fluxoTCP(createMatchToPacket(sw, HTTP_PORT, cntx, originalIp, dstIp), my13Factory, actionsTo);
                                
                                /* Escrita dos fluxos na tabela de fluxos */
                                sw.write(flowFromTCP);
                                sw.write(flowToTCP);
                                logger.info("Fluxos agregados para o request " + uri);
                                logger.info("Receptores nas portas: " + originalPort + " e " + userPort);
                                gets.clear();
                                
                                /*  Barra o pacote para ele nao seguir para o servidor,
                                 *  pois já sera transmitido para o segundo usuário pelo
                                 *  fluxo que foi instalado, que possui saida para o primeiro
                                 *  e segundo usuário
                                 */
                                return Command.STOP;    // FIX: PACOTE DO SEGUNDO USER CONTINUA?!
                                
                            } else {
                                logger.info("GET repetido, fluxos nao agregados por ser o mesmo cliente");
                                return Command.CONTINUE;
                            }
                        }
                    } else {
                        return Command.CONTINUE;
                    }
                } else {
                    return Command.CONTINUE;
                }
            }
        }
        return Command.CONTINUE;
    }
    
    private OFFlowAdd fluxoTCP(Match match, OFFactory myFactory, List<OFAction> actions) {
        
        /* Montagem dos atributos do fluxo através da lista actions e o match */
        
        Set<OFFlowModFlags> flags = new HashSet<>();
        flags.add(OFFlowModFlags.SEND_FLOW_REM);
        
        OFFlowAdd flowToTCP = myFactory
                              .buildFlowAdd()
                              .setActions(actions)
                              .setBufferId(OFBufferId.NO_BUFFER)
                              .setIdleTimeout(100)
                              .setHardTimeout(0)
                              .setMatch(match)
                              .setCookie(U64.of(1L << 59))
                              .setPriority(FlowModUtils.PRIORITY_HIGH)
                              .build();
        return flowToTCP;
    }
    
    private Match createMatchFromPacket(IOFSwitch sw, TransportPort port, FloodlightContext cntx, IPv4Address srcIp, IPv4Address dstIp) {
        
        /*  Criação do Match para o fluxo de transmissao de vídeo,
         *  essa trasnsmissão parte do server com destino ao usuário.
         */
        
        Match.Builder mb = sw.getOFFactory().buildMatch();
        
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
          .setExact(MatchField.IPV4_SRC, dstIp)             // IP server
          .setExact(MatchField.IPV4_DST, srcIp)             // IP cliente
          .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
          .setExact(MatchField.TCP_DST,  port);             // porta de transmissao do cliente
    
        return mb.build();
    }
    
    private Match createMatchToPacket(IOFSwitch sw, TransportPort port, FloodlightContext cntx, IPv4Address srcIp, IPv4Address dstIp) {
        
        /* Criação do match de resposta do cliente para o server (ACK). */
        
        Match.Builder mb = sw.getOFFactory().buildMatch();
        
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
          .setExact(MatchField.IPV4_SRC, srcIp)
          .setExact(MatchField.IPV4_DST, dstIp)
          .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
          .setExact(MatchField.TCP_DST,  port);              // HTTP_PORT (5001)
        
        return mb.build();
    }
}

class Request {
    
    /* 
     *  Classe Request que armazena as informações do request:
     *  - Qual vídeo foi requisitado;
     *  - MAC do usuário que requisitou o vídeo;
     *  - IP do usuário que requisitou o vídeo; e
     *  - Porta do Switch do usuário que requisitou o vídeo.
     */
    
    private String video;
    private MacAddress receiverMac;
    private IPv4Address receiverAddress;
    private TransportPort tcpPort;
    private int receiverPort;
    
    public Request(String video, MacAddress receiverMac, IPv4Address receiverAddress, int receiverPort, TransportPort tcpPort) {
        this.video = video;
        this.receiverMac = receiverMac;
        this.receiverAddress = receiverAddress;
        this.receiverPort = receiverPort;
        this.tcpPort = tcpPort;
    }
    
    public Request(String video) {
        this.video = video;
    }
    
    public Request(String video, int receiverPort) {
        this.video = video;
        this.receiverPort = receiverPort;
    }
    
    public Request(String video, TransportPort tcpPort) {
        this.video = video;
        this.tcpPort = tcpPort;
    }
    
    public String getVideoId() {
        return video;
    }
    
    public MacAddress getMacAddress() {
        return receiverMac;
    }
    
    public IPv4Address getIpAddress() {
        return receiverAddress;
    }
    
    public int getPort() {
        return receiverPort;
    }
    
    public TransportPort getTcpPort() {
        return tcpPort;
    }
}

class Comparador implements Comparator<Request> {
    
    /* 
     *  Classe para comparar os itens das listas,
     *  tem como objetivo encontrar os itens
     */
    
    public int compare(Request e1, Request e2) {
        if(e1.getVideoId() == e2.getVideoId()) {
            return 1;  
        } else {
            return 0;
        }
    }
}
