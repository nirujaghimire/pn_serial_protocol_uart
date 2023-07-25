package tools;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Stack;

public class SerialUART {
    /*This section is for special can send or receive no registered callback get called*/
    private static final int ADAPTER_ID = 0x01;
    private volatile String adapterReceive = "";
    private static final String ADAPTER_REQUEST = "ARQ";
    private static final String ADAPTER_RESPONSE = "ARS";
    private static final String FLASH_ENABLE_REQUEST = "FERQ";
    private static final String FLASH_ENABLE_RESPONSE = "FERS";
    private static final String FLASH_DISABLE_REQUEST = "FDRQ";
    private static final String FLASH_DISABLE_RESPONSE = "FDRS";

    /***********************************************************************************/

    private static final int START = 0;
    private static final int ID = 1;
    private static final int LEN = 2;
    private static final int DATA = 3;
    private static final int END = 4;

    private static final int IDLE = 0;
    private static final int SENDING = 1;
    private static final int RECEIVING = 2;


    private final CRC32 crc32;
    private final SerialPort port;

    private static final long TRANSMIT_RECEIVE_TIMEOUT = 3000;
    private static final int TRANSMIT_TRY = 3;

    private static final long RECEIVE_TIMEOUT = 3000;

    private CanTransmitCallback canTransmitCallback =(status)-> {};
    private UartTransmitCallback uartTransmitCallback =()-> {};
    private CanReceiveCallback canReceiveCallback =(id, bytes)-> {};
    private UartReceiveCallback uartReceiveCallback =(bytes)-> {};
    private ConnectCallback connectCallback =()-> {};
    private DisconnectCallback disconnectCallback =()-> {};

    private final Queue<Byte> receiveQueue = new ArrayDeque<>();
    private final Stack<Integer> statusStack = new Stack<>();

    ///////////////////////////////FOR DEBUG///////////////////////////////////////
    private enum ConsoleStatus{ERROR,INFO,WARNING};
    private void console(ConsoleStatus status,String msg){
        StackTraceElement element = new Exception().getStackTrace()[1];

        if(status==ConsoleStatus.ERROR) {
            System.out.println(ConsoleColors.RED+element+":"+msg+ConsoleColors.RESET);
//            Thread.dumpStack();
        }else if(status==ConsoleStatus.INFO){
//            System.out.println(ConsoleColors.GREEN+element+":"+msg+ConsoleColors.RESET);
        }else if(status==ConsoleStatus.WARNING){
//            System.out.println(ConsoleColors.YELLOW+element+":"+msg+ConsoleColors.RESET);
//            Thread.dumpStack();
        }else{
            System.out.println(element+":"+msg);
        }
    }

    ///////////////////////////////HARDWARE CALLBACK////////////////////////////////
    private int bytesToBeReceived = 1;
    /**
     * It is called when there is data received in input buffer
     */
    private void dataReceivedCallback(byte[] receivedBytes){
        if(statusStack.peek() == IDLE){
            uartReceiveCallback.uartReceiveCallback(receivedBytes);
            return;
        } else if (statusStack.peek() == RECEIVING) {
            if((System.currentTimeMillis()-tic)>RECEIVE_TIMEOUT && receiveStatus!=START){
                receiveStatus = START;
                bytesToBeReceived = 1;
                console(ConsoleStatus.ERROR,"Receive timeout "+RECEIVE_TIMEOUT+" ms");
            }
        }

        for(byte b:receivedBytes)
            receiveQueue.add(b);
        int size = receiveQueue.size();
        if(size>=bytesToBeReceived){
            byte[] bytes = new byte[size];
            Byte b;
            for(int i=0;i<size;i++) {
                b = receiveQueue.poll();
                if(b==null)
                    break;
                bytes[i] = b;
            }
            receivedBytes(bytes);
        }
    }

    /**
     * It is called when port is disconnected
     */
    private void disconnectedCallback(){
        disconnectCallback.disconnectCallback();
    }

    /**
     * It is called when data is written
     */
    private void dataWrittenCallback(){
        if(statusStack.peek()==IDLE){
            uartTransmitCallback.uartTransmitCallback();
        }
    }

    /////////////////////////////////PRIVATE///////////////////////////////

    private volatile boolean isByteReceived = false;
    private byte[] bytesReceived;
    /**
     * This is called whenever there is required data specified in @bytesToBeReceived
     * @param bytes     : bytes received
     */
    private void receivedBytes(byte[] bytes){
        if(statusStack.peek()==SENDING){
            bytesReceived = bytes;
            isByteReceived = true;
        }else if(statusStack.peek()==RECEIVING){
            canReceive(bytes);
        }
    }

    private boolean isAdapter(){
        long tic = System.currentTimeMillis();
        long TIMEOUT = 10000;

        enableCanMode();
        adapterReceive = "";
        send(ADAPTER_ID,ADAPTER_REQUEST.getBytes());
        while(!adapterReceive.equals(ADAPTER_RESPONSE) && System.currentTimeMillis()-tic<TIMEOUT)
            Thread.onSpinWait();
        return adapterReceive.equals(ADAPTER_RESPONSE);
    }

    ////////////////////////////////////CAN TRANSMIT AND RECEIVE/////////////////////////////////////

    private enum SendAndAckStatus{ERROR,SUCCESS,INCORRECT_ACK}
    /**
     * This will send the bytes and wait for acknowledge
     * @param bytes                 : bytes to be sent
     * @param ack                   : ack to be received
     * @param time_out              : timeout in millisecond
     * @param num_of_try            : number of try
     * @param returnInIncorrectAck  : return in incorrect ack if true
     * @return                      : SendAndAckStatus
     */
    private SendAndAckStatus sendAndAck(byte[] bytes,byte[] ack,long time_out,int num_of_try,boolean returnInIncorrectAck){
        for(int j=0;j<num_of_try;j++) {
            if(!port.isOpen())
                return SendAndAckStatus.ERROR;

            /* Sending bytes */
            int writingStatus =  port.writeBytes(bytes, bytes.length);
            if(writingStatus<0){
                /* Error Debugging */
                StringBuilder builder = new StringBuilder();
                builder.append("[ ");
                for(int i=0;i<Math.min(bytes.length,8);i++)
                    builder.append(bytes[i]).append(" ");

                if(bytes.length>8)
                    builder.append(".... ");
                builder.append("]");

                if(j<(num_of_try-1)) {
                    console(ConsoleStatus.WARNING, "Retrying to write bytes ");
                    continue;
                } else {
                    console(ConsoleStatus.ERROR, "Writing bytes " + builder + " failed");
                    return SendAndAckStatus.ERROR;
                }
            }

            long tic = System.currentTimeMillis();
            isByteReceived = false;
            bytesToBeReceived = ack.length;

            console(ConsoleStatus.INFO,"Bytes written and waiting for bytes to be received...");
            while ((System.currentTimeMillis() - tic) <= time_out) {
                if(!port.isOpen())
                    return SendAndAckStatus.ERROR;

                if(!isByteReceived)
                    continue;

                // Check if ack to be received is received
                if(Arrays.equals(ack,bytesReceived)) {
                    console(ConsoleStatus.INFO,"Acknowledge received success");
                    return SendAndAckStatus.SUCCESS;
                } else if(returnInIncorrectAck){
                    return SendAndAckStatus.INCORRECT_ACK;
                }
                console(ConsoleStatus.WARNING,"Acknowledge received is incorrect and waiting for acknowledge again...");
            }
            if(j<(num_of_try-1))
                console(ConsoleStatus.WARNING,"Retrying to get acknowledge...");
        }
        console(ConsoleStatus.ERROR,"Failed to get acknowledge");
        return SendAndAckStatus.ERROR;
    }

    public static enum CanTransmitStatus {ERROR,SUCCESS,CRC_FAILED}
    /**
     * This transmits CAN message
     * @param id        : ID of bytes
     * @param bytes     : Bytes to be sent
     * @return          : CanTransmitStatus
     */
    private CanTransmitStatus canTransmit(long id, byte[] bytes){
        long timeout = TRANSMIT_RECEIVE_TIMEOUT;//Timeout in Millisecond
        int num_try = 2;//Num of try
        statusStack.push(SENDING);
        //Send 'S':'O'
        if(sendAndAck("S".getBytes(),"O".getBytes(),timeout,num_try,false)==SendAndAckStatus.ERROR) {
            console(ConsoleStatus.ERROR,"Start byte: ['S','O'] failed");
            statusStack.pop();
            return CanTransmitStatus.ERROR;
        }
        console(ConsoleStatus.INFO,"Start byte: ['S','O'] success");

        //Send id:id
        byte[] id_bytes = IntegerAndBytes.int32ToBytes(id);
        if(sendAndAck(id_bytes,id_bytes,timeout,num_try,false)==SendAndAckStatus.ERROR) {
            console(ConsoleStatus.ERROR,"ID bytes: ["+String.format("[0x%x,0x%x]",id,id)+" failed");
            statusStack.pop();
            return CanTransmitStatus.ERROR;
        }
        console(ConsoleStatus.INFO,"ID bytes: "+String.format("[0x%x,0x%x]",id,id)+" success");

        //Send len:'O'
        byte[] len_bytes = IntegerAndBytes.int16ToBytes(bytes.length);
        if(sendAndAck(len_bytes,len_bytes,timeout,num_try,false)==SendAndAckStatus.ERROR) {
            console(ConsoleStatus.ERROR,"Length bytes: "+String.format("[%d,%d]",bytes.length,bytes.length)+" failed");
            statusStack.pop();
            return CanTransmitStatus.ERROR;
        }
        console(ConsoleStatus.INFO,"Length bytes: "+String.format("[%d,%d]",bytes.length,bytes.length)+" success");

        //Send Data:crc
        int crc = crc32.calculate(bytes);
        byte[] crc_bytes = IntegerAndBytes.int32ToBytes(crc);
        SendAndAckStatus crcStatus = sendAndAck(bytes,crc_bytes,timeout,num_try,true);
        if(crcStatus==SendAndAckStatus.ERROR){
            console(ConsoleStatus.ERROR,"CRC bytes: "+String.format("[0x%x,0x%x]",crc,crc)+" failed");
            statusStack.pop();
            return CanTransmitStatus.ERROR;
        }else if(crcStatus==SendAndAckStatus.INCORRECT_ACK)
            console(ConsoleStatus.WARNING,"CRC bytes: "+String.format("[0x%x,0x%x]",crc,crc)+" failed");
        else if(crcStatus==SendAndAckStatus.SUCCESS)
            console(ConsoleStatus.INFO,"CRC bytes: "+String.format("[0x%x,0x%x]",crc,crc)+" success");


        //Send '\0' or -1 :'O'
        if(sendAndAck(crcStatus==SendAndAckStatus.SUCCESS?("\0".getBytes()):(new byte[]{-1}),"O".getBytes(),timeout,num_try,false)==SendAndAckStatus.ERROR) {
            console(ConsoleStatus.ERROR,"End bytes: ['\0' or -1,'O'] failed");
            statusStack.pop();
            return CanTransmitStatus.ERROR;
        }
        console(ConsoleStatus.INFO,"End bytes: ['\0' or -1,'O'] success");

        statusStack.pop();
        return crcStatus==SendAndAckStatus.SUCCESS?CanTransmitStatus.SUCCESS:CanTransmitStatus.CRC_FAILED;
    }

    /**
     * This transmits message directly
     * @param bytes     : Bytes to be transmitted
     */
    private void uartTransmit(byte[] bytes){
        port.writeBytes(bytes,bytes.length);
    }

    private int receiveStatus = START;
    private byte[] receiveData;
    private long can_ID;
    private long tic = 0;
    /**
     * This is called in each time received event is called
     * @param bytes     : bytes received
     */
    private void canReceive(byte[] bytes){
        boolean is_failed = false;
        if(receiveStatus == START){
            if(Arrays.equals(bytes,"S".getBytes())){
                //Start byte
                bytesToBeReceived = 4;
                receiveStatus++;
                console(ConsoleStatus.INFO,"Start byte 'S' received successfully");
                if(port.writeBytes("O".getBytes(),1)<=0){
                    console(ConsoleStatus.ERROR,"Start byte ack 'O' sending failed");
                    is_failed = true;
                }else {
                    console(ConsoleStatus.INFO, "Start byte ack 'O' sent successfully");
                }
            }
        }else if(receiveStatus == ID){
            //ID
            can_ID = IntegerAndBytes.bytesToInt32(bytes);
            bytesToBeReceived = 2;
            receiveStatus++;
            console(ConsoleStatus.INFO,"ID:"+String.format("0x%x",can_ID)+" received successfully");
            if(port.writeBytes(bytes,4)<=0){
                console(ConsoleStatus.ERROR,"Ack ID:"+String.format("0x%x",can_ID)+" sending failed");
                is_failed = true;
            }else {
                console(ConsoleStatus.INFO, "Ack ID:" + String.format("0x%x", can_ID) + " sent successfully");
            }
        }else if(receiveStatus == LEN){
            //LEN
            bytesToBeReceived = IntegerAndBytes.bytesToInt16(bytes);
            receiveStatus++;
            console(ConsoleStatus.INFO,"Length:"+bytesToBeReceived+" received successfully");
            if(port.writeBytes(bytes,2)<=0){
                console(ConsoleStatus.ERROR,"Ack Length:"+bytesToBeReceived+" sending failed");
                is_failed = true;
            }else{
                console(ConsoleStatus.INFO,"Ack Length:"+bytesToBeReceived+" sent successfully");
            }
        }else if(receiveStatus == DATA){
            //DATA
            bytesToBeReceived = 1;
            receiveStatus++;
            receiveData = bytes;
            int crc = crc32.calculate(receiveData);
            console(ConsoleStatus.INFO,"Data received successfully");
            if(port.writeBytes(IntegerAndBytes.int32ToBytes(crc),4)<=0){
                console(ConsoleStatus.ERROR,"Ack CRC:"+String.format("0x%x",crc)+" sending failed");
                is_failed = true;
            }else{
                console(ConsoleStatus.INFO,"Ack CRC:"+String.format("0x%x",crc)+" sent successfully");
            }
        }else if(receiveStatus == END){
            //END
            bytesToBeReceived = 1;
            receiveStatus = START;
            if(Arrays.equals("\0".getBytes(),bytes)) {
                if(can_ID==ADAPTER_ID){
                    StringBuilder builder = new StringBuilder();
                    for(byte b:receiveData)
                        builder.append((char)b);
                    adapterReceive = builder.toString();
                }else
                    canReceiveCallback.canReceiveCallback(can_ID, receiveData);
            }
            console(ConsoleStatus.INFO,"End byte '"+bytes[0]+"' received successfully");
            if(port.writeBytes("O".getBytes(),1)<=0){
                console(ConsoleStatus.ERROR,"End byte ack 'O' sending failed");
                is_failed = true;
            }else {
                console(ConsoleStatus.INFO, "End byte ack 'O' sent successfully");
            }
        }

        tic = System.currentTimeMillis();
        if(is_failed){
            receiveStatus = START;
            bytesToBeReceived = 1;
        }
    }

    /////////////////////////////////PUBLIC////////////////////////////////
    public SerialUART(SerialPort port,int baud_rate){
        this.crc32 = new CRC32();
        this.port = port;

        this.statusStack.push(IDLE);
        this.port.setBaudRate(baud_rate);

        int event_type = SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                |SerialPort.LISTENING_EVENT_DATA_WRITTEN
                |SerialPort.LISTENING_EVENT_DATA_RECEIVED;

        new SerialPortEvent(port,event_type);
        this.port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return event_type;
            }

            @Override
            public void serialEvent(SerialPortEvent serialPortEvent) {
                if(serialPortEvent.getEventType()==SerialPort.LISTENING_EVENT_DATA_RECEIVED)
                    dataReceivedCallback(serialPortEvent.getReceivedData());
                else if(serialPortEvent.getEventType()==SerialPort.LISTENING_EVENT_PORT_DISCONNECTED)
                    disconnectedCallback();
                else if(serialPortEvent.getEventType()==SerialPort.LISTENING_EVENT_DATA_WRITTEN)
                    dataWrittenCallback();
            }
        });

    }

    /**
     * It connects the uart port
     * @return      : Status of connection
     */
    public boolean connect(){
        if(port.openPort()) {
            connectCallback.connectCallback();
            return true;
        }
        return false;
    }

    /**
     * It disconnects the port
     * @return      : Status of disconnection
     */
    public boolean disconnect(){
        if(port.closePort()) {
            disconnectCallback.disconnectCallback();
            return true;
        }
        return false;
    }

    /**
     * This method send the data
     * @param id        : Can ID of data bytes
     * @param bytes     : data bytes
     */
    public void send(int id,byte[] bytes){
        new Thread(()->{
            CanTransmitStatus status;
            for (int i = 0; i < TRANSMIT_TRY; i++) {
                if(!port.isOpen())
                    return;
                status = canTransmit(id, bytes);
                if(status == CanTransmitStatus.SUCCESS) {
                    console(ConsoleStatus.INFO,"Data sent with ID: "+id);
                    if(id!=ADAPTER_ID)
                        canTransmitCallback.canTransmitCallback(CanTransmitStatus.SUCCESS);
                    return;
                }else if (status == CanTransmitStatus.CRC_FAILED) {
                    console(ConsoleStatus.WARNING,"Data sent with ID: "+id+" (CRC Failed)");
                    if(id!=ADAPTER_ID)
                        canTransmitCallback.canTransmitCallback(CanTransmitStatus.CRC_FAILED);
                    return;
                }
                console(ConsoleStatus.WARNING,"Retrying to send data with ID: "+id);
            }
            console(ConsoleStatus.ERROR,"Failed to send data with ID: "+id);
            if(id!=ADAPTER_ID)
                canTransmitCallback.canTransmitCallback(CanTransmitStatus.ERROR);
        }).start();
    }

    /**
     * This method send the data through UART
     * @param bytes     : data bytes to be sent
     */
    public void send(byte[] bytes){
        new Thread(()->uartTransmit(bytes)).start();
    }

    /**
     * It enables CAN transmit and receive mode
     */
    public void enableIdleMode(){
        statusStack.clear();
        statusStack.push(IDLE);
    }

    /**
     * It enables normal transmit and receive mode
     */
    public void enableCanMode(){
        statusStack.clear();
        statusStack.push(RECEIVING);
    }

    /**
     * It enables the flash mode
     * @return is flash enabled?
     */
    public boolean enableFlashMode(){
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        long tic = System.currentTimeMillis();
        long TIMEOUT = 10000;

        enableCanMode();
        adapterReceive = "";
        send(ADAPTER_ID,FLASH_ENABLE_REQUEST.getBytes());
        while(!adapterReceive.equals(FLASH_ENABLE_RESPONSE) && System.currentTimeMillis()-tic<TIMEOUT)
            Thread.onSpinWait();
        return adapterReceive.equals(FLASH_ENABLE_RESPONSE);
    }

    /**
     * It disables the flash mode
     * @return is flash disabled?
     */
    public boolean disableFlashMode(){
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        long tic = System.currentTimeMillis();
        long TIMEOUT = 10000;

        enableCanMode();
        send(ADAPTER_ID,FLASH_DISABLE_REQUEST.getBytes());
        while(!adapterReceive.equals(FLASH_DISABLE_RESPONSE) && System.currentTimeMillis()-tic<TIMEOUT)
            Thread.onSpinWait();
        return adapterReceive.equals(FLASH_DISABLE_RESPONSE);
    }

    //////////////////////////////////SET CALLBACK//////////////////////////
    /**
     * Callback triggers after data is either sent successfully or failed
     * @param canTransmitCallback      : Callback function
     * @return                  : this
     */
    public SerialUART setCanTransmitCallback(CanTransmitCallback canTransmitCallback){
        this.canTransmitCallback = canTransmitCallback;
        return this;
    }

    /**
     * Callback triggers after data is received successfully
     * @param canReceiveCallback   : Callback function
     * @return                  : this
     */
    public SerialUART setCanReceiveCallback(CanReceiveCallback canReceiveCallback){
        this.canReceiveCallback = canReceiveCallback;
        return this;
    }

    /**
     * Callback triggers after data is either sent successfully or failed
     * @param uartTransmitCallback      : Callback function
     * @return                  : this
     */
    public SerialUART setUartTransmitCallback(UartTransmitCallback uartTransmitCallback){
        this.uartTransmitCallback = uartTransmitCallback;
        return this;
    }

    /**
     * Callback triggers after data is received successfully
     * @param uartReceiveCallback   : Callback function
     * @return                  : this
     */
    public SerialUART setUartReceiveCallback(UartReceiveCallback uartReceiveCallback){
        this.uartReceiveCallback = uartReceiveCallback;
        return this;
    }

    /**
     * Callback triggers after connected
     * @param connectCallback   : Callback function
     * @return                  : this
     */
    public SerialUART setConnectCallback(ConnectCallback connectCallback){
        this.connectCallback = connectCallback;
        return this;
    }

    /**
     * Callback triggers after disconnected
     * @param disconnectCallback: Callback function
     * @return                  : this
     */
    public SerialUART setDisconnectCallback(DisconnectCallback disconnectCallback){
        this.disconnectCallback = disconnectCallback;
        return this;
    }

    //////////////////////////////////INTERFACE//////////////////////////////
    @FunctionalInterface
    public static interface CanTransmitCallback {
        void canTransmitCallback(CanTransmitStatus status);
    }

    @FunctionalInterface
    public static interface CanReceiveCallback {
        void canReceiveCallback(long id, byte[] bytes);
    }

    @FunctionalInterface
    public static interface UartReceiveCallback{
        void uartReceiveCallback(byte[] bytes);
    }

    @FunctionalInterface
    public static interface UartTransmitCallback{
        void uartTransmitCallback();
    }

    @FunctionalInterface
    public static interface ConnectCallback{
        void connectCallback();
    }

    @FunctionalInterface
    public static interface DisconnectCallback{
        void disconnectCallback();
    }

    /////////////////////////////STATIC CLASS/////////////////////////
    public static SerialPort detectAdapter(){
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports == null)
            return null;
        if (ports.length == 0)
            return null;

        SerialPort port = null;
        for (SerialPort p : ports) {
            System.out.println(p.getDescriptivePortName());

            SerialUART uart = new SerialUART(p,115200);
            if(uart.connect()){
                if(uart.isAdapter()) {
                    port = p;
                    port.removeDataListener();
                    break;
                }
            }
        }

        if(port!=null)
            System.out.println("Adapter :"+port.getDescriptivePortName());
        else
            System.out.println("Adapter not found");
        return port;
    }


}