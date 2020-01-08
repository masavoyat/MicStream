# MicStream
MicStream is an Android app that stream microphone (intgrated or jack) over UDP.
<br>
Parameters are configured in the main application page and streaming is done by a foreground service.
<br><br>
Parameters are:
- Destination IP
- Destination port
- Sample size 8/16 bits
- (To be implemented) Sampling frequency
- (To be implemented) Compression

## UDP frame format
The frame format is inspired from RTP frame format
### Header

|Element|Size (bytes)|Description|
|:-----:|:----------:|:----------:|
|firstByte|1|First byte is always 0x80|
|payloadType|1|Byte indicate the payload type and how to read it.<br>See section below for more information|
|sequenceNumber|2|An number incrementing with every UDP frame indicating the sequence ordering|
|timeStamp |4|Currently implemented as sample counter|
|samplingFrequency |4|The sampling frequency of the recording device in Hz|

Total header size is 12 bytes.

### Payload types

|PayloadType|Sample Size|Description|
|:---:|:---:|:---:|
|127| 16 bits | Raw signed 16 bits samples|
|126| 8 bits | Raw signed 16 bits samples|
