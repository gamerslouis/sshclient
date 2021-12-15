package nctu.winlab.sshclient;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface VlanSwitch {
    public ObjectNode setSwitchPortMode(String intf, String mode);
    public ObjectNode setSwitchPortAccessVlan(String intf, String vlan);
    public ObjectNode setVlanGateway(String vlan, String ip, String mask);
    public ObjectNode showVlan();
    public ObjectNode addVlan(String vlanId);
    public ObjectNode deleteVlan(String vlanId);
}
