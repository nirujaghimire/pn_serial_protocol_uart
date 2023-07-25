package tools;

public class IntegerAndBytes {
    private static int byteToInt(byte b){
        return (0x000000FF & (int)b);
    }

    private static long byteToLong(byte b){
        return (0x00000000000000FF & (long)b);
    }

    public static byte[] int64ToBytes(long value){
        byte[] bytes = new byte[8];
        bytes[0] = (byte) value;
        bytes[1] = (byte) (value>>8);
        bytes[2] = (byte) (value>>16);
        bytes[3] = (byte) (value>>24);
        bytes[4] = (byte) (value>>32);
        bytes[5] = (byte) (value>>40);
        bytes[6] = (byte) (value>>48);
        bytes[7] = (byte) (value>>56);
        return bytes;
    }

    public static long bytesToInt64(byte[] bytes){
        long value = byteToLong(bytes[7]);
        value =(value<<8)| byteToLong(bytes[6]);
        value =(value<<8)| byteToLong(bytes[5]);
        value =(value<<8)| byteToLong(bytes[4]);
        value =(value<<8)| byteToLong(bytes[3]);
        value =(value<<8)| byteToLong(bytes[2]);
        value =(value<<8)| byteToLong(bytes[1]);
        value =(value<<8)| byteToLong(bytes[0]);
        return value;
    }

    public static byte[] int32ToBytes(long value){
        byte[] bytes = new byte[4];
        bytes[0] = (byte) value;
        bytes[1] = (byte) (value>>8);
        bytes[2] = (byte) (value>>16);
        bytes[3] = (byte) (value>>24);
        return bytes;
    }

    public static long bytesToInt32(byte[] bytes){
        long value = byteToInt(bytes[3]);
        value =(value<<8)| byteToInt(bytes[2]);
        value =(value<<8)| byteToInt(bytes[1]);
        value =(value<<8)| byteToInt(bytes[0]);
        return value;
    }

    public static byte[] int16ToBytes(int value){
        byte[] bytes = new byte[2];
        bytes[0] = (byte) value;
        bytes[1] = (byte) (value>>8);
        return bytes;
    }

    public static int bytesToInt16(byte[] bytes){
        int value = byteToInt(bytes[1]);
        value =(value<<8)| byteToInt(bytes[0]);
        return value;
    }

}
