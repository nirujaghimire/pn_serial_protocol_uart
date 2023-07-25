package flash;

import tools.CRC32;
import tools.IntegerAndBytes;
import tools.SerialUART;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class FlashDisplay {
    //CAN ID
    private final int JUMP_TO_BOOTLOADER_ID = 0x18ABCDEF;
    private final int JUMP_TO_BOOTLOADER_ACK_ID = 0x18FF7028;
    private final int CRC_ID = 0x18EF2802;
    private final int FILE_SIZE_ID = 0x18EF2803;
    private final int ERASE_ACK = 0x18EF2809;
    private final int DATA_ID = 0x18EF2805;
    private final int VERSION_LEN_ID = 0x18EF2806;
    private final int VERSION_ID = 0x18EF2807;
    private final int UPLOAD_SUCCESS_STATUS_ID = 0x18EF2810;

    //MAX data chunk
    private final int MAX_DATA_CHUNK = 1024;

    //TIMEOUT
    private final int TIMEOUT = 10000;//in ms

    private final SerialUART uart;
    private final CRC32 crc32;

    public FlashDisplay(SerialUART uart){
        this.uart = uart;
        this.crc32 = new CRC32();
    }

    private volatile boolean received = false;
    private volatile byte[] received_data = new byte[8];
    public boolean receiveCallback(long id,byte[] bytes){
        switch ((int) id) {
            case JUMP_TO_BOOTLOADER_ACK_ID,
                    CRC_ID,
                    FILE_SIZE_ID,
                    ERASE_ACK,
                    DATA_ID,
                    VERSION_LEN_ID,
                    VERSION_ID,
                    UPLOAD_SUCCESS_STATUS_ID -> {
//                System.out.println("Received Callback");
                received = true;
//                System.arraycopy(bytes,0,received_data,0,bytes.length);
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

            //JUMP TO BOOTLOADER
            uart.send(JUMP_TO_BOOTLOADER_ID, new byte[]{2});//2 for Display
            //WAIT FOR BOOT ACK
            if(isAckFailed(null)) return false;

            //CRC
            int crc = crc32.calculate(filename);
            uart.send(CRC_ID, IntegerAndBytes.int32ToBytes(crc));
            //WAIT FOR CRC ACK
            if(isAckFailed(null)) return false;
            System.out.printf("File CRC32 : 0x%x\n",crc);

            //FILE SIZE
            uart.send(FILE_SIZE_ID, IntegerAndBytes.int32ToBytes(file_size));
            //WAIT FOR FILE SIZE ACK
            if(isAckFailed(null)) return false;
            System.out.println("File size : "+file_size+" bytes");

            //WAIT FOR ERASE ACK
            if(isAckFailed(null)) return false;

            int file_ptr = 0;
            int new_size;
            long prevTime = System.currentTimeMillis();
            while(true){
                new_size = file_size-file_ptr;
                if(new_size>=MAX_DATA_CHUNK)
                    new_size = MAX_DATA_CHUNK;
                byte[] bytes;

                //DATA
                bytes = fileReader.readNBytes(new_size);
                file_ptr+=new_size;
                uart.send(DATA_ID,bytes);
//                System.out.println(Arrays.toString(bytes));
                //WAIT FOR DATA ACK
                byte[] ack = new byte[8];
                ack[0] = 4;
                byte[] f_ptr = IntegerAndBytes.int32ToBytes(file_ptr);
                for (int i = 0; i < 4; i++)
                    ack[i+1] = f_ptr[i];
                if(isAckFailed(null)) return false;

                System.out.println(file_ptr*100/file_size+"% "+(System.currentTimeMillis()-prevTime)+" ms");
                if(file_ptr>=file_size)
                    break;
            }


            String version = "1.1.1";
            //VERSION LENGTH
            uart.send(VERSION_LEN_ID, new byte[]{(byte) version.length()});
            //WAIT FOR VERSION LENGTH ACK
            if(isAckFailed(null)) return false;

            //VERSION
            uart.send(VERSION_ID, version.getBytes());
            //WAIT FOR VERSION ACK
            if(isAckFailed(null)) return false;
            System.out.println("UPLOADED");

            //WAIT FOR UPLOAD STATUS
            if(isAckFailed(null)) return false;
            System.out.println("FLASHED SUCCESSFULLY ;P");

            fileReader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

}








































