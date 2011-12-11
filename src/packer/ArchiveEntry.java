package packer;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;

/**
 * A basic archive entry. Contains the actual pixels + meta (conversion from
 * pixel data to GX happens here)
 * 
 * @author emu_kidid
 * 
 */
public class ArchiveEntry {
	private ArchiveEntryMeta	metaData;
	private byte[]				gxTexture;
	private Image				pngImg;

	// Examples of Hi-Res texture naming
	// SUPER MARIO 64#0B6D2926#0#2_all.png
	// SUPER MARIO 64#D8903B0B#0#2_rgb.png
	// SUPER MARIO 64#9FBECEF9#0#2_a.png
	// WAVE RACE 64#FAF51949#2#1#897BF8BE_ciByRGBA.png (with paletteCRC)

	// If there is a '_all' file, use it and disregard the rest (_rgb,_a)
	// If there is a '_rgb' file, look for a '_a' file for alpha, if not just
	// use '_rgb'

	/**
	 * Reads the meta data out of a file name, reads the png pixel data from the
	 * file, converts it to a GX texture and GZIP compresses it.
	 * 
	 * @param primaryFile
	 * @param alphaFile
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public ArchiveEntry(File primaryFile, File alphaFile) throws InterruptedException, IOException, Exception {

		System.out.println("Process: " + primaryFile.getName());
		if (alphaFile != null) {
			System.out.println("Extra: " + alphaFile.getName());
		}
		int[] primaryPixels = null;
		int[] alphaPixels = null;
		boolean replaceAlphaWithB = StringUtils.startsWithIgnoreCase(
				StringUtils.substringAfter(StringUtils.substringAfter(primaryFile.getName(), "#"), "#"), "4#");

		metaData = new ArchiveEntryMeta();
		metaData.setFileName(primaryFile.getAbsolutePath());

		// Decompress the primary PNG
		pngImg = Toolkit.getDefaultToolkit().getImage(metaData.getFileName());
		ImageFrame primaryFrame = new ImageFrame(pngImg, metaData.getFileName(), false);
		metaData.setWidth(pngImg.getWidth(primaryFrame));
		metaData.setHeight(pngImg.getHeight(primaryFrame));

		// Grab the primary PNG pixels
		File f = new File(metaData.getFileName());
		BufferedImage bufferedImage = ImageIO.read(f);
		metaData.setRawPixelType(getType(bufferedImage.getType()));
		primaryPixels = bufferedImage.getRGB(0, 0, metaData.getWidth(), metaData.getHeight(), primaryPixels, 0,
				metaData.getWidth());

		// Decompress the alpha PNG if it exists
		if (alphaFile != null) {
			Image alphaImg = Toolkit.getDefaultToolkit().getImage(alphaFile.getAbsolutePath());
			ImageFrame secondaryFrame = new ImageFrame(alphaImg, alphaFile.getAbsolutePath(), false);
			metaData.setAlphaWidth(alphaImg.getWidth(secondaryFrame));
			metaData.setAlphaHeight(alphaImg.getHeight(secondaryFrame));
			BufferedImage alphaImage = ImageIO.read(alphaFile);
			metaData.setRawAlphaPixelType(getType(alphaImage.getType()));
			alphaPixels = alphaImage.getRGB(0, 0, metaData.getAlphaWidth(), metaData.getAlphaHeight(), alphaPixels, 0,
					metaData.getAlphaWidth());
			secondaryFrame.dispose();
		}

		// Convert 24bit or 24bit + alpha/etc
		if (bufferedImage.getType() == BufferedImage.TYPE_3BYTE_BGR) {
			if (alphaPixels != null && alphaPixels.length != primaryPixels.length) {
				throw new Exception("Alpha data cannot differ in dimensions from RGB data");
			}
			gxTexture = convertTex24bpp(primaryPixels, alphaPixels, replaceAlphaWithB);
		}
		// Else, simple 32 bit RGBA conversion
		else if (bufferedImage.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
			gxTexture = convertTex32bpp(primaryPixels);
		}

		// Compress it
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		GZIPOutputStream gz = new GZIPOutputStream(bos);
		gz.write(gxTexture);
		gz.flush();
		gz.close();

		gxTexture = bos.toByteArray();
		metaData.setCompressedLength(gxTexture.length);

		// Standardise directory separators across platforms
		metaData.setFileName(StringUtils.substringAfterLast(StringUtils.replace(metaData.getFileName(), "\\", "/"), "/"));

		String textureInfo = StringUtils.substringAfter(metaData.getFileName(), "#");

		metaData.setTextureCRC(Long.parseLong(StringUtils.substringBefore(textureInfo, "#"), 16));
		textureInfo = StringUtils.substringAfter(textureInfo, "#");
		metaData.setFormat(Short.parseShort(StringUtils.substringBefore(textureInfo, "#"), 16));
		textureInfo = StringUtils.substringAfter(textureInfo, "#");

		// PaletteCRC might be supplied
		if (StringUtils.contains(textureInfo, "#")) {
			metaData.setSize(Short.parseShort(StringUtils.substringBefore(textureInfo, "#"), 16));
			textureInfo = StringUtils.substringAfter(textureInfo, "#");
			metaData.setPaletteCRC(Long.parseLong(StringUtils.substringBefore(textureInfo, "_"), 16));
			textureInfo = StringUtils.substringAfter(textureInfo, "_");
		} else {
			metaData.setPaletteCRC(new Long(-1));
			metaData.setSize(Short.parseShort(StringUtils.substringBefore(textureInfo, "_"), 16));
			textureInfo = StringUtils.substringAfter(textureInfo, "_");
		}

		metaData.setType(StringUtils.substringBefore(textureInfo, "."));
		primaryFrame.dispose();
	}

	private String getType(int type) {
		switch (type) {
			case BufferedImage.TYPE_4BYTE_ABGR:
				return "4 Byte ABGR (6)";
			case BufferedImage.TYPE_3BYTE_BGR:
				return "4 Byte BGR (5)";
		}
		return "Unknown";
	}

	/**
	 * Converts a 32 Bit RGBA Pixel Array into a GX Array
	 * 
	 * @param data
	 * @return
	 */
	public byte[] convertTex32bpp(int[] data) {
		byte R, G, B, A;
		int color, x, y, ind = 0;
		int w = metaData.getWidth() - (metaData.getWidth() % 4);
		int h = metaData.getHeight() - (metaData.getHeight() % 4);
		short[] GX_buffer = new short[(int) (h * w * 2)];

		for (int i = 0; i < h; i += 4) {
			for (int j = 0; j < w; j += 4) {
				for (int ii = 0; ii < 4; ii++) {
					for (int jj = 0; jj < 4; jj++) {
						x = j + jj;
						y = i + ii;
						color = data[(int) ((y * w) + x)];
						R = (byte) ((color >> 16) & 0xFF);
						G = (byte) ((color >> 8) & 0xFF);
						B = (byte) ((color >> 0) & 0xFF);
						A = (byte) ((color >> 24) & 0xFF);
						GX_buffer[ind] = (short) (((A & 0xFF) << 8) | (R & 0xFF));
						GX_buffer[ind + 16] = (short) (((G & 0xFF) << 8) | (B & 0xFF));
						ind++;
					}
				}
				ind += 16;
			}
		}
		byte[] gxTextureBytes = new byte[GX_buffer.length * 2];
		for (int i = 0; i < GX_buffer.length; i++) {
			gxTextureBytes[i * 2] = (byte) (GX_buffer[i] >> 8);
			gxTextureBytes[(i * 2) + 1] = (byte) (GX_buffer[i] & 0xFF);
		}
		return gxTextureBytes;
	}

	/**
	 * Converts a 24 Bit RGB Pixel Array into a GX Array, either with alpha in a
	 * seperate buff or 0xFF or replacing it with B
	 * 
	 * @param data
	 * @param needsAlpha
	 * @param alphaPixels
	 * @param replaceAlphaWithB
	 * @return
	 */
	public byte[] convertTex24bpp(int[] primaryPixels, int[] alphaPixels, boolean replaceAlphaWithB) {
		byte R, G, B, A;
		int x, y, ind = 0;
		int w = metaData.getWidth() - (metaData.getWidth() % 4);
		int h = metaData.getHeight() - (metaData.getHeight() % 4);
		short[] GX_buffer = new short[(int) (h * w * 2)];

		for (int i = 0; i < h; i += 4) {
			for (int j = 0; j < w; j += 4) {
				for (int ii = 0; ii < 4; ii++) {
					for (int jj = 0; jj < 4; jj++) {
						x = j + jj;
						y = i + ii;
						int color = primaryPixels[(int) ((y * w) + x)];
						R = (byte) ((color >> 16) & 0xFF);
						G = (byte) ((color >> 8) & 0xFF);
						B = (byte) ((color >> 0) & 0xFF);
						if (alphaPixels != null) {
							A = (byte) (alphaPixels[(int) ((y * w) + x)] & 0xFF);
						} else if (replaceAlphaWithB) {
							A = (byte) B;
						} else {
							A = (byte) 0xFF;
						}

						GX_buffer[ind] = (short) (((A & 0xFF) << 8) | (R & 0xFF));
						GX_buffer[ind + 16] = (short) (((G & 0xFF) << 8) | (B & 0xFF));
						ind++;
					}
				}
				ind += 16;
			}
		}
		byte[] gxTextureBytes = new byte[GX_buffer.length * 2];
		for (int i = 0; i < GX_buffer.length; i++) {
			gxTextureBytes[i * 2] = (byte) (GX_buffer[i] >> 8);
			gxTextureBytes[(i * 2) + 1] = (byte) (GX_buffer[i] & 0xFF);
		}
		return gxTextureBytes;
	}

	public byte[] getGXTexture() {
		return gxTexture;
	}

	public Image getPngImg() {
		return pngImg;
	}

	public ArchiveEntryMeta getMeta() {
		return metaData;
	}
}

@SuppressWarnings("serial")
class ImageFrame extends Frame {
	private static final int	XBORDER	= 20;
	private static final int	YBORDER	= 50;
	private Image				img;

	public void paint(Graphics g) {
		g.drawImage(img, XBORDER, YBORDER, this);
	}

	public ImageFrame(Image img, String title, boolean showVisible) {
		this.img = img;
		setTitle(title);
		while (img.getWidth(this) <= 0) {
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		}
		setSize(img.getWidth(this) + XBORDER * 2, img.getHeight(this) + YBORDER * 2);
		setVisible(showVisible);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				super.windowClosing(arg0);
				setVisible(false);
			}
		});
	}
}
