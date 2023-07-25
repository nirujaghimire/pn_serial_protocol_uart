import com.fazecast.jSerialComm.SerialPort;
import tools.SerialUART;

public class Main {
    static volatile boolean transmitted = true;
    public static void test(SerialUART uart){
        uart.enableCanMode();
        uart.setCanReceiveCallback((id, bytes) -> {
            System.out.printf("0x%x (%d) : ",id,bytes.length);
            for (byte aByte : bytes)
                System.out.printf("%c", aByte);
            System.out.println();
            transmitted = true;
        });

        uart.setCanTransmitCallback(status -> {
            if(status != SerialUART.CanTransmitStatus.SUCCESS)
                System.out.println("failed");
            else
               System.out.println("success");
        });
        System.out.println(uart.connect() ? "Connected" : "Connection failed");
        transmitted = true;
        byte[] bytes = "Yes this is Peter Speaking, Niruja!!!".getBytes();
        for(int i=1;i<=(10);i++) {
            while (!transmitted)
                Thread.onSpinWait();
            System.out.printf("Sending : 0x%x => ",i+1);
            transmitted = false;
            uart.send(i+1, bytes);
        }
    }

    public static void main(String[] args) {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports == null)
            return;
        if (ports.length == 0)
            return;
        SerialPort port = null;
        for (SerialPort p : ports) {
            System.out.println(p.getDescriptivePortName());
            if(p.getDescriptivePortName().contains("COM4"))
                port = p;
        }

        if(port==null) {
            System.out.println("NULL PORT");
            return;
        }
        SerialUART uart = new SerialUART(port, 115200);
        test(uart);
    }
}