package flash;

import tools.CRC32;
import tools.IntegerAndBytes;
import tools.SerialUART;

import java.io.*;

public class Flash {
    //to adapter
    private final int START_ID = 0x1;
    private final int DATA_START_ID = 0x2;
    private final int DATA_ID = 0x3;
    private final int DATA_END_ID = 0x4;
    private final int CRC_ID = 0x5;

    //from adapter
    private final int READY = 0x6;

    //MAX data chunk
    private final int MAX_DATA_CHUNK = 8;

    //TIMEOUT
    private final int TIMEOUT = 10000;//in ms

    private final SerialUART uart;

    private final CRC32 crc32;

    public Flash(SerialUART uart){
        this.uart = uart;
        crc32 = new CRC32();
    }

    private volatile boolean received = false;
    private volatile byte[] received_data = new byte[8];
    public boolean receiveCallback(long id,byte[] bytes){
//        System.out.println("Received");

        switch ((int) id) {
            case READY-> {
//                System.out.println("Received Callback");
                received = true;
                System.arraycopy(bytes,0,received_data,0,bytes.length);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void transmittedCallback(){
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        received = true;
    }

    private boolean isAckFailed(byte[] ack_data){
//        System.out.println("Received");
        received = false;
        long prevTime = System.currentTimeMillis();
        while (true) {

            if(!received)
                continue;
            if(ack_data!=null){
                boolean match = true;
                for (int i = 0; i <8 ; i++) {
                    match = received_data[i] != ack_data[i];
                    if(!match)
                        break;
                }
                if(match)
                    return false;
            }else{
                return false;
            }

            if (System.currentTimeMillis() - prevTime >= TIMEOUT)
                return true;
        }
//        return true;
    }

    public boolean flash(String filename){
        uart.enableCanMode();
        File file = new File(filename);
        try {
            InputStream fileReader = new FileInputStream(file);
            int file_size = (int) file.length();


            //START
            byte[] b =  IntegerAndBytes.int64ToBytes(file_size);
            uart.send(START_ID, b);
            //WAIT FOR READY
            if(isAckFailed(null)) return false;

            System.out.println("File size : "+file_size+" bytes");

            int file_ptr = 0;
            int new_size;
            long prevTime = System.currentTimeMillis();
            while(true){
                new_size = file_size-file_ptr;
                if(new_size>=MAX_DATA_CHUNK)
                    new_size = MAX_DATA_CHUNK;
                byte[] bytes;

                //DATA START
                uart.send(DATA_START_ID, IntegerAndBytes.int64ToBytes(new_size));
                //WAIT FOR READY
                if(isAckFailed(null)) return false;

                //DATA
                bytes = fileReader.readNBytes(new_size);
                file_ptr+=new_size;
                uart.send(DATA_ID,bytes);
                //WAIT FOR READY
                if(isAckFailed(null)) return false;

                //END
                uart.send(DATA_END_ID, IntegerAndBytes.int64ToBytes(new_size));
                //WAIT FOR READY
                if(isAckFailed(null)) return false;

                System.out.println(file_ptr*100/file_size+"% "+(System.currentTimeMillis()-prevTime)+" ms");
                if(file_ptr>=file_size)
                    break;
            }

            //CRC
            uart.send(CRC_ID, IntegerAndBytes.int64ToBytes(100));
            //WAIT FOR READY
            if(isAckFailed(null)) return false;

            fileReader.close();
            System.out.println("Uploaded :P");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}
