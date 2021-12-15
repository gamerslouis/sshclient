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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DXS5000Client extends SshShellClient implements SwitchClient, VxlanSwitch, VlanSwitch {
    private static Logger log = Logger.getLogger(DXS5000Client.class.getName());

    public DXS5000Client(String ip, String port, String username, String password, String model) {
        super(ip, port, username, password);
        this.model = model;
    }

    @Override
    public ObjectNode getController() {
        ObjectNode res = createGeneralReply();
        ArrayNode controllerList = res.putArray("controllers");
        String rawoutput = "";
        try {
            String[] reply = commander.addCmd("enable").addMainCmd("show openflow configured controller", new String[0]).addCmd("exit").sendCmd().recvCmd().split("[\r\n]+");
            String[] controllers = Arrays.copyOfRange(reply, 3, reply.length);
            rawoutput += String.format("%-17s%-7s%-6s%s\n", "IP", "Port", "Mode", "Role");
            for (String controller : controllers) {
                String[] infos = (String[])Stream.of(controller.split("[ \t]+")).filter(i -> !i.isEmpty()).toArray(x$0 -> new String[x$0]);
                ObjectNode c = mapper().createObjectNode();
                rawoutput += String.format("%-17s%-7s%-6s%s\n", infos[0], infos[1], infos[2], infos[3]);
                c.put("ip", infos[0]);
                c.put("port", infos[1]);
                c.put("mode", infos[2]);
                c.put("role", infos[3]);
                controllerList.add((JsonNode) c);
            }
            res.put("raw", rawoutput);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setController(String ip, String port) {
        ObjectNode res = createGeneralReply();
        String proto = "tcp";
        try {
            String reply = commander.addCmd("enable", "configure").addMainCmd("openflow controller " + ip + " " + port + " " + proto).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode unsetController(String ip) {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addMainCmd("no openflow controller " + ip).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode getFlows() {
        ObjectNode res = createGeneralReply();
        ArrayNode flowList = res.putArray("flows");
        try {
            String reply = commander.addCmd("enable").addMainCmd("show openflow installed flows", " ", " ", " ", " ").addCmd("exit").sendCmd().recvCmd();
            res.put("raw", reply);
            for (String flow : reply.split("(?=\r\nFlow type)")) {
                ObjectNode flowNode = mapper().createObjectNode();
                String[] items = flow.split("(Match criteria:|Actions:|Status:)");
                flowNode.put("type", processFlowType(items[0]));
                flowNode.set("matches", (JsonNode)processKeyValue(items[1]));
                flowNode.set("actions", (JsonNode)processKeyValue(items[2]));
                flowNode.set("status", (JsonNode)processKeyValue(items[3]));
                // add flow into flow list
                flowList.add((JsonNode)flowNode);
            }
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode getGroups() {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable").addMainCmd("show openflow installed groups", " ", " ", " ", " ").addCmd("exit").sendCmd().recvCmd();
            res.put("raw", reply);
            res.set("groups", (JsonNode) processGroups(reply));
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public void getLogs(FileWriter writer) {
        try {
            String reply = commander.addCmd("enable").addMainCmd("show logging buffered", "q").addCmd("exit").sendCmd().recvCmd();
            String title = String.format(ANSI_GREEN + ANSI_BOLD + "\n%s -- %s\n" + ANSI_RESET, ip, model);
            if (writer == null) {
                log.info(title);
                log.info(reply);
            } else {
                writer.write(title, 0, title.length());
                writer.write(reply, 0, reply.length());
            }
        }
        catch (Exception e) {
            return;
        }
    }

    @Override
    public ObjectNode setVxlanSourceInterfaceLoopback(String loopbackId) {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addCmd("vxlan enable").addMainCmd("vxlan source-interface loopback " + loopbackId, new String[0]).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setVxlanVlan(String vnid, String vid) {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addCmd("vxlan enable").addMainCmd("vxlan " + vnid + " vlan " + vid, new String[0]).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setVxlanVtep(String vnid, String ip, String mac) {
        log.info(vnid + " " + ip + " " + mac);
        log.info("vxlan " + vnid + " vtep " + ip + (mac.isEmpty() ? "" : new StringBuilder().append(" tenant-system ").append(mac).toString()));
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addCmd("vxlan enable").addMainCmd("vxlan " + vnid + " vtep " + ip + (mac.isEmpty() ? "" : new StringBuilder().append(" tenant-system ").append(mac).toString()), new String[0]).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setVxlanStatus(boolean flag) {
        ObjectNode res = createGeneralReply();
        try {
            String isopen = flag ? "" : "no ";
            String reply = commander.addCmd("enable", "configure").addMainCmd(isopen + "vxlan enable").addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setVxlanTenantSystemLocal(String vni, String mac, String port, boolean add) {
        ObjectNode res = createGeneralReply();
        try {
            String isopen = add ? "" : "no ";
            String reply = commander.addCmd("enable", "configure").addCmd("interface " + port).addMainCmd(isopen + "vxlan " + vni + " tenant-system " + mac).addCmd("exit", "exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setVxlanTenantSystemRemote(String vni, String mac, String remoteIp, boolean add) {
        ObjectNode res = createGeneralReply();
        try {
            String isopen = add ? "" : "no ";
            String reply = commander.addCmd("enable", "configure").addMainCmd(isopen + "vxlan " + vni + " vtep " + remoteIp + " tenant-system " + mac).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode showVxlanTenantSystemLocal() {
        ObjectNode res = createGeneralReply();
        ArrayNode hostList = res.putArray("hosts");
        try {
            String[] reply = commander.addCmd("enable").addMainCmd("show vxlan tenant-systems local", new String[0]).addCmd("exit").sendCmd().recvCmd().split("[\r\n]+");
            String[] hosts = Arrays.copyOfRange(reply, 3, reply.length);
            for (String host : hosts) {
                String[] infos = (String[])Stream.of(host.split("[ \t]+")).filter(i -> !i.isEmpty()).toArray(x$0 -> new String[x$0]);
                ObjectNode c = mapper().createObjectNode();
                c.put("vni", infos[0]);
                c.put("mac", infos[1]);
                c.put("port", infos[2]);
                c.put("appIfIndex", infos[3]);
                c.put("entryType", infos[4]);
                hostList.add((JsonNode) c);
            }
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode showVxlanTenantSystemRemote() {
        ObjectNode res = createGeneralReply();
        ArrayNode hostList = res.putArray("hosts");
        try {
            String[] reply = commander.addCmd("enable").addMainCmd("show vxlan tenant-systems remote", new String[0]).addCmd("exit").sendCmd().recvCmd().split("[\r\n]+");
            String[] hosts = Arrays.copyOfRange(reply, 3, reply.length);
            for (String host : hosts) {
                String[] infos = (String[])Stream.of(host.split("[ \t]+")).filter(i -> !i.isEmpty()).toArray(x$0 -> new String[x$0]);
                ObjectNode c = mapper().createObjectNode();
                c.put("vni", infos[0]);
                c.put("mac", infos[1]);
                c.put("vtep", infos[2]);
                c.put("appIfIndex", infos[3]);
                c.put("entryType", infos[4]);
                hostList.add((JsonNode) c);
            }
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode showVxlan() {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addMainCmd("show vxlan").addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode testConnection() {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addMainCmd("enable", "configure").addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @Override
    public ObjectNode setSwitchPortMode(String intf, String mode) {
        ObjectNode res = createGeneralReply();
        log.info(intf + " " + mode);
        log.info(mode == null ? "no switchport mode": ("switchport mode " + mode));

        try {
            String reply = commander.addCmd("enable", "configure").addCmd("interface "+ intf).addMainCmd(mode == null ? "no switchport mode": ("switchport mode " + mode)).addCmd("exit", "exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    
    @Override
    public ObjectNode setSwitchPortAccessVlan(String intf, String vlan) {
        log.info(intf + " " + vlan);
        log.info("switchport access vlan " + vlan);
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addCmd("interface " + intf).addMainCmd("switchport access vlan " + vlan).addCmd("exit", "exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }
    
    @Override
    public ObjectNode setVlanGateway(String vlan, String ip, String mask) {
        ObjectNode res = createGeneralReply();
        try {
            String reply = commander.addCmd("enable", "configure").addCmd("interface vlan " + vlan).addMainCmd("routing", "ip address " + ip + " " + mask + "\n").addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        log.info(res.toString());
        return res;
    }
    
    @Override
    public ObjectNode showVlan() {
        ObjectNode res = createGeneralReply();
        ArrayNode vlanList = res.putArray("vlans");

        try {
            String[] reply = commander.addCmd("enable").addMainCmd("show vlan", new String[0]).addCmd("exit").sendCmd().recvCmd().split("[\r\n]+");
            String[] vlans = Arrays.copyOfRange(reply, 3, reply.length);

            for (String vlan : vlans) {
                String[] infos = (String[])Stream.of(vlan.split("[ \t]+")).filter(i -> !i.isEmpty()).toArray(x$0 -> new String[x$0]);
                ObjectNode c = mapper().createObjectNode();
                c.put("id", infos[0]);
                c.put("name", infos[1]);
                c.put("type", infos[2]);
                vlanList.add((JsonNode) c);
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
            String reply = commander.addCmd("enable", "vlan database").addMainCmd("vlan " + vlanId).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
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
            String reply = commander.addCmd("enable", "vlan database").addMainCmd("no vlan " + vlanId).addCmd("exit", "exit").sendCmd().recvCmd();
            res.put("raw", reply);
        }
        catch (Exception e) {
            res.put("error", true);
            res.put("msg", e.getMessage());
        }
        return res;
    }


    private String processFlowType(String flowType) {
        return flowType.replace("Flow type ", "").replace("DOT", ".").replaceAll("\r\n|\"", "");
    }

    private ObjectNode processKeyValue(String raw) {
        String[] lineFields = raw.split("\r\n");
        ObjectNode dictNode = mapper().createObjectNode();
        for (String line : lineFields) {
            String[] patterns = line.split(" : ");
            if ((line = line.strip()).length() == 0)
                continue;
            for (String pat : patterns) {
                String value = pat.replaceFirst("([a-zA-Z\\s]+)(?<!\\w{2}:\\w{2}:\\w{2})", "");
                String key = pat.replace(value, "").strip();
                value = value.replace(":", "").strip();
                dictNode.put(key, value);
            }
        }
        return dictNode;
    }

    private ArrayNode processGroups(String raw) {
        ObjectNode group = null;
        ObjectNode bucket = null;
        ArrayNode groups = mapper().createArrayNode();
        ArrayNode buckets = null;
        String[] rawGroups = raw.split("[\r\n]+");
        String[] reducedRawGroups = Arrays.copyOfRange(rawGroups, 7, rawGroups.length);
        int state = -1;
        for (String line : reducedRawGroups) {
            String key;
            Matcher matcher;
            String value;
            Pattern pattern;
            if (line.indexOf("Group Id") == 0) {
                state = 0;
                group = mapper().createObjectNode();
                groups.add((JsonNode)group);
                buckets = group.putArray("buckets");
                pattern = Pattern.compile("(\\d+)");
                matcher = pattern.matcher(line);
                if (!matcher.find()) continue;
                group.put("id", Integer.parseInt(matcher.group(1).strip()));
                continue;
            }
            if (line.contains("Group Type")) {
                state = 1;
                pattern = Pattern.compile("Group Type : (.*)");
                matcher = pattern.matcher(line);
                if (!matcher.find()) continue;
                group.put("type", matcher.group(1).strip());
                continue;
            }
            if (line.contains("Bucket entry list for group")) {
                state = 2;
                bucket = mapper().createObjectNode();
                buckets.add((JsonNode)bucket);
                continue;
            }
            if (state == 1) {
                pattern = Pattern.compile("([a-zA-Z\\s]+):[\\s]+(\\d+)");
                matcher = pattern.matcher(line);
                while (matcher.find()) {
                    key = matcher.group(1).strip();
                    value = matcher.group(2).strip();
                    group.put(key, value);
                }
                continue;
            }
            if (state != 2) continue;
            pattern = Pattern.compile("([a-zA-Z\\s]+|Output port):?[\\s]+(NA|\\w{2}(:\\w{2}){5}|\\d+(\\/\\d+)?)");
            matcher = pattern.matcher(line);
            while (matcher.find()) {
                key = matcher.group(1).strip();
                value = matcher.group(2).strip();
                if (key.contains("Index")) continue;
                bucket.put(key, value);
            }
        }
        return groups;
    }
}
