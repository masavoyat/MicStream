# MicStream
MicStream is an Android app that stream microphone (intgrated or jack) over UDP.
<br>
Parameters are configured in the main application page and streaming is done by a foreground service.
<br><br>
Parameters are:
- Destination IP
- Destination port
- Payload Type: Sample size 8/16 bits, Compression
- Sampling frequency
<br>
An example of Stream receiver and decoder is implemented here:
https://github.com/masavoyat/MicStreamServer

## UDP frame format
The frame format is inspired from RTP frame format
### Header

|Element|Size (bytes)|Description|
|:-----:|:----------:|:----------:|
|firstByte|1|First byte is always 0x80|
|payloadTypeId|1|Byte indicate the payload type and how to read it.<br>See section below for more information|
|sequenceNumber|2|An number incrementing with every UDP frame indicating the sequence ordering|
|timeStamp |4|Currently implemented as sample counter|
|samplingFrequency |4|The sampling frequency of the recording device in Hz|

Total header size is 12 bytes.

### Payload types

|PayloadType|PayloadTypeId|Sample Size|Description|
|:---:|:---:|:---:|:---:|
|RAW 16BIT|127| 16 bits | Raw signed 16 bits samples|
|RAW 8BIT|126| 8 bits | Raw signed 8 bits samples|
|ZIP 16BIT|125| 16 bits | Zip compressed signed 16 bits samples|
|ZIP 8BIT|124| 8 bits | Zip compressed signed 8 bits samples|

