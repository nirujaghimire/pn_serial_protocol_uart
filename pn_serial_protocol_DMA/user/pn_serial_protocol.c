/*
 * uart.c
 *
 *  Created on: Jan 25, 2023
 *      Author: NIRUJA
 */
#include "pn_serial_protocol.h"
#include "stdarg.h"
#include "string.h"

#define RECEIVE_BUFF_SIZE 2048
#define TRANSMIT_BUFF_SIZE 100

#define ADAPTER_ID 0x0
#define ADAPTER_REQUEST "ARQ"
#define ADAPTER_RESPONSE "ARS"
#define FLASH_ENABLE_REQUEST "FERQ"
#define FLASH_ENABLE_RESPONSE "FERS"
#define FLASH_DISABLE_REQUEST "FDRQ"
#define FLASH_DISABLE_RESPONSE "FDRS"

static UART_HandleTypeDef *huart_adapter;
static CRC_HandleTypeDef *hcrc_adapter;

static void (*receiveCallback)(uint32_t, uint8_t*, uint16_t);

typedef enum {
	SENDING, RECEIVING
} Status;

typedef enum {
	START = 0, ID, LEN, DATA, END
} ReceiveTrack;

typedef enum {
	CONSOLE_ERROR, CONSOLE_INFO, CONSOLE_WARNING
} ConsoleStatus;

static Status status = RECEIVING;

//For Receiving
static const uint32_t RECEIVE_TIMEOUT = 1000;
static const uint32_t RECEIVE_TRANSMIT_TIMEOUT = 10000;
static uint8_t rec_data[RECEIVE_BUFF_SIZE];
static uint8_t rec_sync_bytes[1];
static uint8_t rec_id_bytes[4];
static uint8_t rec_len_bytes[2];
static ReceiveTrack rec_track = START;
static uint32_t time_elapse;
static uint8_t received = 0;
static uint32_t received_id = 0;
static uint16_t received_len = 0;
static uint8_t is_in_receiveThread = 0;

//For Sending
static const uint32_t TRANSMIT_TIMEOUT = 1000;
static const int TRANSMIT_TRY = 3;
static uint8_t send_data[TRANSMIT_BUFF_SIZE];
static uint8_t send_sync_bytes[1];
static uint8_t send_sync_ack[1];

static void receiveThreadDebug(const char *msg) {
//	printf("%s\n", msg);
}

static void console(ConsoleStatus status, const char *func_name,
		const char *msg, ...) {
	//	if(state!=CONSOLE_ERROR)
	if (status == CONSOLE_INFO)
		return;
	//TODO make naked and show all registers
	if (status == CONSOLE_ERROR) {
		printf("uart.c|%s> ERROR :", func_name);
	} else if (status == CONSOLE_INFO) {
		printf("uart.c|%s> INFO : ", func_name);
	} else if (status == CONSOLE_WARNING) {
		printf("uart.c|%s> WARNING : ", func_name);
	} else {
		printf("uart.c|%s: ", func_name);
	}
	va_list args;
	va_start(args, msg);
	vprintf(msg, args);
	va_end(args);
}

///////////////////////////////////////////////////////////
static uint8_t rec_ack[8];
/*
 * It will send bytes and wait for acknowledge
 * @param bytes         : bytes to be sent
 * @param bytes_len     : size of bytes
 * @param ack           : ack to be received
 * @param ack_len       : size of ack
 * @param time_out      : timeout in millisecond
 * @param num_of_try    : number of try
 * @return              : 1 for success
 *                      : 0 for failed
 */
static uint8_t sendAndack(uint8_t *bytes, uint16_t bytes_len, uint8_t *ack,
		uint16_t ack_len, uint32_t time_out, uint8_t num_of_try) {
	uint8_t success_check = 0;
	for (int i = 0; i < num_of_try; i++) {
		if (HAL_UART_Transmit(huart_adapter, bytes, bytes_len, time_out)
				!= HAL_OK) {
			console(CONSOLE_ERROR, __func__, "A2P Transmit error, try no %d\n",
					i);
			continue;
		}
		HAL_UART_Receive(huart_adapter, rec_ack, ack_len, time_out);
		uint8_t check = 1;
		for (int j = 0; j < ack_len; j++) {
			if (rec_ack[j] != ack[j]) {
				check = 0;
				console(CONSOLE_ERROR, __func__, "A2P Ack match error :: \n");
				break;
			}
		}
		if (check) {
			success_check = 1;
			break;
		}
	}
	return success_check;
}

/**
 * It starts the receiving process or make program to wait for data from PC
 */
static void startReceiving() {
	rec_track = START;
	status = RECEIVING;
	HAL_UART_Receive_DMA(huart_adapter, rec_sync_bytes, 1);
	console(CONSOLE_INFO, __func__,
			"Waiting for start byte 'S' to be received\n");
}

/**
 * This is receive thread called each time there is a receive available
 */
static void receiveThread() {
	is_in_receiveThread = 1;
	uint8_t is_failed = 0;
	if (rec_track == START) {
		if (rec_sync_bytes[0] == 'S') {
			rec_sync_bytes[0] = 'O';
			rec_track++;
			status = RECEIVING;
			console(CONSOLE_INFO, __func__, "Start byte %c received\n",
					(char) rec_sync_bytes[0]);
			if (HAL_UART_Transmit(huart_adapter, rec_sync_bytes, 1,
					RECEIVE_TRANSMIT_TIMEOUT) != HAL_OK) {
				console(CONSOLE_ERROR, __func__, "Start ack %c send failed\n",
						(char) rec_sync_bytes[0]);
				is_failed = 1;
			} else {
				console(CONSOLE_INFO, __func__, "Start ack %c sent\n",
						(char) rec_sync_bytes[0]);
				HAL_UART_Receive_DMA(huart_adapter, rec_id_bytes, 4);
				console(CONSOLE_INFO, __func__,
						"Waiting for CAN ID to be received \n");
			}
		} else {
			startReceiving();
		}
	} else if (rec_track == ID) {
		rec_track++;
		console(CONSOLE_INFO, __func__, "CAN ID received :: 0x%x \n",
				*(uint8_t*) rec_id_bytes);
		if (HAL_UART_Transmit(huart_adapter, rec_id_bytes, 4,
				RECEIVE_TRANSMIT_TIMEOUT) != HAL_OK) {
			console(CONSOLE_ERROR, __func__, "CAN ID ack 0x%x sending failed\n",
					*(uint8_t*) rec_id_bytes);

			is_failed = 1;
		} else {
			console(CONSOLE_INFO, __func__, "CAN ID ack 0x%x sent\n",
					*(uint8_t*) rec_id_bytes);
			HAL_UART_Receive_DMA(huart_adapter, rec_len_bytes, 2);
			console(CONSOLE_INFO, __func__,
					"Waiting for length to be received\n");
		}
	} else if (rec_track == LEN) {
		console(CONSOLE_INFO, __func__, "Start byte %c received\n",
				*(uint8_t*) rec_len_bytes);
		rec_track++;
		if (HAL_UART_Transmit(huart_adapter, rec_len_bytes, 2,
				RECEIVE_TRANSMIT_TIMEOUT) != HAL_OK) {
			console(CONSOLE_INFO, __func__,
					"Length ack transmission failed \n");
			is_failed = 1;
		} else {
			console(CONSOLE_INFO, __func__, "Length ack transmitted : %d \n",
					*((uint16_t*) rec_len_bytes));
			HAL_UART_Receive_DMA(huart_adapter, rec_data,
					*((uint16_t*) rec_len_bytes));
			receiveThreadDebug("LEN");
			console(CONSOLE_INFO, __func__,
					"Waiting for data to be received\n");
		}

	} else if (rec_track == DATA) {
		uint32_t len = *((uint16_t*) rec_len_bytes);
		int loop_limit = len % 4;
		for (int i = 0; i < (4 - loop_limit) && loop_limit > 0; i++)
			rec_data[len++] = 0x00;
		uint32_t crc = HAL_CRC_Calculate(hcrc_adapter, (uint32_t*) rec_data,
				len / 4);
		rec_track++;
		console(CONSOLE_INFO, __func__, "Data received\n");
		if (HAL_UART_Transmit(huart_adapter, (uint8_t*) &crc, 4,
				RECEIVE_TRANSMIT_TIMEOUT) != HAL_OK) {
			console(CONSOLE_ERROR, __func__,
					"Data ack CRC %0x0x transmission failed \n", crc);
			is_failed = 1;
		} else {
			console(CONSOLE_INFO, __func__, "Data ack CRC transmitted :  %d \n",
					crc);
			HAL_UART_Receive_DMA(huart_adapter, rec_sync_bytes, 1);
			console(CONSOLE_INFO, __func__, "Waiting for end byte received \n");
			receiveThreadDebug("DATA");
		}
	} else if (rec_track == END) {
		console(CONSOLE_INFO, __func__, "End byte received : %c \n",
				(char) rec_sync_bytes[0]);
		received_id = *((uint32_t*) rec_id_bytes);
		received_len = *((uint16_t*) rec_len_bytes);
		if (rec_sync_bytes[0] == '\0') {
			received = 1;
			console(CONSOLE_INFO, __func__,
					"Data received successfully(CRC matched)\n");
		} else {
			console(CONSOLE_ERROR, __func__,
					"Data end ack receive failed(CRC didn't match)\n");
		}
		rec_sync_bytes[0] = 'O';
		if (HAL_UART_Transmit(huart_adapter, rec_sync_bytes, 1,
				RECEIVE_TRANSMIT_TIMEOUT) != HAL_OK) {

			console(CONSOLE_INFO, __func__, "End ack transmission failed\n");
			is_failed = 1;
		} else {
			console(CONSOLE_INFO, __func__, "End ack transmitted : %c\n",
					(char) rec_sync_bytes[0]);
			startReceiving();
		}
	}

	if (is_failed) {
		console(CONSOLE_ERROR, __func__,
				"receiveThread process failed and process is restarted\n");
		startReceiving();
	}
	time_elapse = HAL_GetTick();
	is_in_receiveThread = 0;
}

/**
 * It start the sending process or stop receiving process
 */
static void startSending() {
	status = SENDING;
	HAL_UART_DMAStop(huart_adapter);
}

/**
 *This sends CAN message
 *@param id		: CAN ID
 *@param bytes	: Bytes to be sent
 *@param len 	: length of bytes
 *@return       : 1 for successs
 *				: 0 for failed
 */
static uint8_t sendThread(uint32_t id, uint16_t len) {
	startSending();
	uint32_t timeout = TRANSMIT_TIMEOUT;
	uint8_t num_try = 5;

	send_sync_bytes[0] = 'S';
	send_sync_ack[0] = 'O';

	if (!sendAndack(send_sync_bytes, 1, send_sync_ack, 1, timeout, num_try)) {
		console(CONSOLE_ERROR, __func__,
				"A2P Start ack %c sending and %c ack receive error\n",
				(char) send_sync_bytes[0], (char) send_sync_ack[0]);
		return 0;
	}
	console(CONSOLE_INFO, __func__, "A2P Start send and receive ack %c & %c\n",
			(char) send_sync_bytes[0], (char) send_sync_ack[0]);

	if (!sendAndack((uint8_t*) (&id), 4, (uint8_t*) (&id), 4, timeout,
			num_try)) {
		console(CONSOLE_ERROR, __func__,
				"A2P Can ID send and ack error for %d number of try\n",
				num_try);
		return 0;
	}
	console(CONSOLE_INFO, __func__,
			"A2P CAN id send and id ack receive successful\n");

	if (!sendAndack((uint8_t*) (&len), 2, (uint8_t*) (&len), 2, timeout,
			num_try)) {
		console(CONSOLE_ERROR, __func__,
				"A2P Length of data %d send and ack %d receive error\n", len,
				len);
		return 0;
	}
	console(CONSOLE_INFO, __func__,
			"A2P Length of data %d send and ack %c receive success\n", len,
			(char) send_sync_ack[0]);

	uint32_t new_len = len;
	int loop_limit = new_len % 4;
	for (int i = 0; i < (4 - loop_limit) && loop_limit > 0; i++)
		send_data[new_len++] = 0x00;
	uint32_t crc = HAL_CRC_Calculate(hcrc_adapter, (uint32_t*) send_data,
			new_len / 4);
	uint8_t check = sendAndack(send_data, len, (uint8_t*) (&crc), 4, timeout,
			num_try);

	send_sync_bytes[0] = check ? '\0' : -1;
	if (send_sync_bytes[0] == '\0') {
		console(CONSOLE_INFO, __func__,
				"A2P Length of data %d send and ack 0x%0x receive success\n",
				len, crc);
	} else {
		console(CONSOLE_ERROR, __func__,
				"A2P Length of data %d send and ack 0x%0x receive error\n", len,
				crc);
	}

	if (!sendAndack(send_sync_bytes, 1, send_sync_ack, 1, timeout, num_try)) {
		console(CONSOLE_ERROR, __func__,
				"A2P Data end byte %c sending and ack %c receiving error\n",
				(char) send_sync_bytes[0], (char) send_sync_ack[0]);
		return 0;
	}
	console(CONSOLE_INFO, __func__,
			"A2P Data end byte %c sent and ack %c received \n",
			(char) send_sync_bytes[0], (char) send_sync_ack[0]);


	startReceiving();
	return check;
}

////////////////////////////////////////////////////////////////////////////
/**
 * This is called at beginning to initiate
 * @param huart	: UART handler
 * @param hcrc	: CRC handler
 * @param huart	: receive callback function
 */
static void init(UART_HandleTypeDef *huart, CRC_HandleTypeDef *hcrc,
		void (*receiveCallbackFunc)(uint32_t, uint8_t*, uint16_t)) {
	huart_adapter = huart;
	hcrc_adapter = hcrc;
	receiveCallback = receiveCallbackFunc;

	startReceiving();
}

/**
 * This transmits data to PC
 * @param id	: CAN ID
 * @param bytes	: bytes to be send
 * @param len	: Length of data to be sent
 */
static int transmit(uint32_t id, uint8_t *bytes, uint16_t len) {
	for (int i = 0; i < len; i++)
		send_data[i] = bytes[i];


	for(int i=0;i<TRANSMIT_TRY;i++){
		if(sendThread(id, len)){
			console(CONSOLE_INFO, __func__, "DATA transmit success\n");
			return 1;
		}
		console(CONSOLE_WARNING, __func__, "DATA transmit failed and retrying...\n");
	}
	console(CONSOLE_ERROR, __func__, "DATA transmit failed\n");
	return 0;
}

/**
 * This method should be called in DMA callback
 */
static void receiveRxCpltCallback() {
	if (status == RECEIVING)
		receiveThread();
	else
		startReceiving();
}

static volatile int flashModeStatus = 0;
/**
 * This checks wheather flash mode is enabled or not
 */
static int isFlashModeEnabled(){
	return flashModeStatus;
}

/**
 * This is uart loop
 *
 */
static void loop() {
	uint8_t is_timeout = (HAL_GetTick() - time_elapse) > RECEIVE_TIMEOUT;
	if (is_timeout && !is_in_receiveThread && rec_track != START) {
		console(CONSOLE_ERROR, __func__, "receiveThread timeout %d ms\n",
				RECEIVE_TIMEOUT);
		startReceiving();
	}

	if (received) {
		if(received_id==ADAPTER_ID){
			if(strcmp(ADAPTER_REQUEST,(char*)rec_data)==0){
				transmit(received_id, (uint8_t*)ADAPTER_RESPONSE, strlen(ADAPTER_RESPONSE));
				printf("Adapter Detected\n");
			}else if(strcmp(FLASH_ENABLE_REQUEST,(char*)rec_data)==0){
				flashModeStatus = 1;
				transmit(received_id, (uint8_t*)FLASH_ENABLE_RESPONSE, strlen(FLASH_ENABLE_RESPONSE));
				printf("Flash Mode Enabled\n");
			}else if(strcmp(FLASH_DISABLE_REQUEST,(char*)rec_data)==0){
				flashModeStatus = 0;
				transmit(received_id, (uint8_t*)FLASH_DISABLE_RESPONSE, strlen(FLASH_DISABLE_RESPONSE));
				printf("Flash Mode Disabled\n");
			}
		}else{
			receiveCallback(received_id, rec_data, received_len);
		}

		received = 0;
	}
}



//////////////////////////////////////////////////////////////////////////////////

struct SerialProtocolControl StaticSerialProtocol = {
		.init = init,
		.loop = loop,
		.receiveRxCpltCallback = receiveRxCpltCallback,
		.transmit = transmit,
		.isFlashModeEnabled = isFlashModeEnabled
};
