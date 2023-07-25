package flash;

import tools.CRC32;
import tools.IntegerAndBytes;
import tools.SerialUART;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FlashVCU {
    //CAN ID
    private final int JUMP_TO_BOOTLOADER_ID = 0x18ABCDEF;
    private final int JUMP_TO_BOOTLOADER_ACK_ID = 0x18FF7028;
    private final int CRC_ID = 0x18EF2801;
    private final int FILE_SIZE_ID = 0x18EF2803;
    private final int ERASE_ACK = 0x18EF2809;
    private final int DATA_ID = 0x18EF2804;
    private final int VERSION_LEN_ID = 0x18EF2806;
    private final int VERSION_ID = 0x18EF2807;
    private final int UPLOAD_SUCCESS_STATUS_ID = 0x18EF2810;

    //MAX data chunk
    private final int MAX_DATA_CHUNK = 1024;

    //TIMEOUT
    private final int TIMEOUT = 30000;//in ms

    private final SerialUART uart;
    private final CRC32 crc32;

    public FlashVCU(SerialUART uart){
        this.uart = uart;
        this.crc32 = new CRC32();
    }

    private volatile boolean received = false;
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
                received = true;
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

    private boolean isAckFailed(){
        received = false;
        long prevTime = System.currentTimeMillis();
        while (!received)
            if (System.currentTimeMillis() - prevTime >= TIMEOUT)
                return true;
        return false;
    }
    public boolean flash(String filename){
        uart.enableCanMode();
        File file = new File(filename);
        try {
            InputStream fileReader = new FileInputStream(file);
            int file_size = (int) file.length();

            //JUMP TO BOOTLOADER
            uart.send(JUMP_TO_BOOTLOADER_ID, new byte[]{1});//1 for VCU
            //WAIT FOR BOOT ACK
            if(isAckFailed()) return false;

            //CRC
            int crc = crc32.calculate(filename);
            uart.send(CRC_ID, IntegerAndBytes.int32ToBytes(crc));
            //WAIT FOR CRC ACK
            if(isAckFailed()) return false;
            System.out.printf("File CRC32 : 0x%x\n",crc);

            //FILE SIZE
            uart.send(FILE_SIZE_ID, IntegerAndBytes.int32ToBytes(file_size));
            //WAIT FOR FILE SIZE ACK
            if(isAckFailed()) return false;
            System.out.println("File size : "+file_size+" bytes");

            //WAIT FOR ERASE ACK
            if(isAckFailed()) return false;

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
                //WAIT FOR DATA ACK
                if(isAckFailed()) return false;

                System.out.println(file_ptr*100/file_size+"% "+(System.currentTimeMillis()-prevTime)+" ms");
                if(file_ptr>=file_size)
                    break;
            }


            String version = "1.1.1";
            //VERSION LENGTH
            uart.send(VERSION_LEN_ID, new byte[]{(byte) version.length()});
            //WAIT FOR VERSION LENGTH ACK
            if(isAckFailed()) return false;

            //VERSION
            uart.send(VERSION_ID, version.getBytes());
            //WAIT FOR VERSION ACK
            if(isAckFailed()) return false;
            System.out.println("UPLOADED");

            //WAIT FOR UPLOAD STATUS
            if(isAckFailed()) return false;
            System.out.println("FLASHED SUCCESSFULLY ;P");

            fileReader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

}








































