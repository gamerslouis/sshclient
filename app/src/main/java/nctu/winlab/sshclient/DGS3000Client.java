package nctu.winlab.sshclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static nctu.winlab.sshclient.SSHConstants.ANSI_BOLD;
import static nctu.winlab.sshclient.SSHConstants.ANSI_GREEN;
import static nctu.winlab.sshclient.SSHConstants.ANSI_RESET;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class DGS3000Client extends SshShellClient implements VlanSwitch {
    private static Logger log = Logger.getLogger(DGS3630Client.class.getName());

    public DGS3000Client(String ip, String port, String username, String password, String model) {
        super(ip, port, username, password);
        this.model = model;
    }

    
    @Override
    public ObjectNode setSwitchPortMode(String intf, String mode) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public ObjectNode setSwitchPortAccessVlan(String intf, String vlan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectNode setVlanGateway(String vlan, String ip, String mask) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public ObjectNode showVlan() {
        ObjectNode res = createGeneralReply();
        ArrayNode vlanList = res.putArray("vlans");

        try {
            String[] reply = commander.addMainCmd("show vlan", "a").sendCmd().recvCmd().split("[\r\n]+");
            String[] lines = Arrays.copyOfRange(reply, 2, reply.length);

            for(int i = 0; i < lines.length; i++) {
                if(lines[i].startsWith("VID")) {
                    String[] infos1 = (String[])Stream.of(lines[i].split("[ \t]+")).filter(j -> !j.isEmpty()).toArray(x$0 -> new String[x$0]);
                    String[] infos2 = (String[])Stream.of(lines[i+1].split("[ \t]+")).filter(j -> !j.isEmpty()).toArray(x$0 -> new String[x$0]);
                 
                    ObjectNode c = mapper().createObjectNode();
                    c.put("id", infos1[2]);
                    c.put("name", infos1[5]);
                    c.put("type", infos2[3]);
                    vlanList.add((JsonNode) c);        
                }
            }
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    
    @Override
    public ObjectNode addVlan(String vlanId) {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addMainCmd("create vlan vlanid " + vlanId).sendCmd().recvCmd();
            String reply2 = commander.addMainCmd("config vlan vlanid " + vlanId + " add tagged 1-28").sendCmd().recvCmd();
            res.put("raw1", reply);
            res.put("raw2", reply);            
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    
    @Override
    public ObjectNode deleteVlan(String vlanId) {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addMainCmd("delete vlan vlanid " + vlanId).sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

}