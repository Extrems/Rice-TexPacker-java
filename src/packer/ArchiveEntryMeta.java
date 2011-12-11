package packer;

/**
 * Contains Meta data for a archive entry
 * 
 * @author emu_kidid
 * 
 */
public class ArchiveEntryMeta {
	private String	type, fileName;
	private Long	textureCRC, paletteCRC, offset;
	private Short	format, size;
	private Integer	compressedLength, width, height;
	private Integer	alphaWidth, alphaHeight;
	private String	rawPixelType, rawAlphaPixelType;

	public ArchiveEntryMeta() {
	}

	public void setTextureCRC(Long textureCRC) {
		this.textureCRC = textureCRC;
	}

	public void setPaletteCRC(Long paletteCRC) {
		this.paletteCRC = paletteCRC;
	}

	public void setFormat(Short format) {
		this.format = format;
	}

	public void setSize(Short size) {
		this.size = size;
	}

	public void setCompressedLength(Integer compressedLength) {
		this.compressedLength = compressedLength;
	}

	public Integer getWidth() {
		return width;
	}

	public void setWidth(Integer width) {
		this.width = width;
	}

	public Integer getHeight() {
		return height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public Long getOffset() {
		return offset;
	}

	public void setOffset(Long offset) {
		this.offset = offset;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public Long getTextureCRC() {
		return textureCRC;
	}

	public Long getCRC64() {
		// unsigned int crc32;
		// unsigned char pal_crc32_byte1;
		// unsigned char pal_crc32_byte2;
		// unsigned char pal_crc32_byte3;
		// unsigned format : 4;
		// unsigned size : 4;
		return (long) (((textureCRC & 0xFFFFFFFF) << 32) | (paletteCRC & 0x00000000FFFFFF00L) | ((format & 0xF) << 4) | (size & 0xF));
	}

	public String toString() {
		String str = fileName + " textureCRC: " + Long.toHexString(textureCRC).toUpperCase() + "(type: " + type
				+ ") paletteCRC: " + Long.toHexString(paletteCRC).toUpperCase() + "\nformat: "
				+ Integer.toHexString(format).toUpperCase() + " size: " + Integer.toHexString(size).toUpperCase()
				+ " width * height: " + width + " x " + height + " compressedLength: " + compressedLength
				+ "\nu64 crc: " + Long.toHexString(getCRC64()).toUpperCase() + " Pixel Data Type: " + rawPixelType
				+ (rawAlphaPixelType != null ? (" Alpha Type: " + rawAlphaPixelType) : "");
		return str;
	}

	public Short getGXFormat() {
		return 6; // GX_TF_RGBA8
	}

	public Integer getAlphaWidth() {
		return alphaWidth;
	}

	public void setAlphaWidth(Integer alphaWidth) {
		this.alphaWidth = alphaWidth;
	}

	public Integer getAlphaHeight() {
		return alphaHeight;
	}

	public void setAlphaHeight(Integer alphaHeight) {
		this.alphaHeight = alphaHeight;
	}

	public void setRawPixelType(String rawPixelType) {
		this.rawPixelType = rawPixelType;
	}

	public void setRawAlphaPixelType(String rawAlphaPixelType) {
		this.rawAlphaPixelType = rawAlphaPixelType;
	}
}
