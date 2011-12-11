package packer;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;

import org.apache.commons.lang.StringUtils;

import component.RiverLayout;

/**
 * The actual archive
 * 
 * Header: u8 magic[4]; //'GXA1'<br>
 * u32 table_location; // LUT location in the pack <br>
 * u32 num_entries; // Number of entries in the LUT
 * 
 * LUT Entry: <br>
 * u64 custom structure made up of the following: <br>
 * unsigned int crc32; <br>
 * unsigned char pal_crc32_byte1; <br>
 * unsigned char pal_crc32_byte2; <br>
 * unsigned char pal_crc32_byte3; <br>
 * unsigned format : 4; <br>
 * unsigned size : 4; <br>
 * u32 offset;
 * 
 * Compressed Entry: <br>
 * u32 width; <br>
 * u32 height; <br>
 * u8 gxtex[compressedSize];
 * 
 * @author emu_kidid
 * 
 */
@SuppressWarnings("serial")
public class Archiver extends JFrame {

	private final String					version			= "v1.0";
	private File							outputArchive;
	private boolean							startPushed		= false;
	private RandomAccessFile				fw;
	private BufferedWriter					logFile;
	private List<File>						textureFiles;
	/* GUI crap */
	private final Dimension					screenDimension	= new Dimension(800, 600);
	private JTextArea						dirTxt			= new JTextArea("Select a Directory ...");
	private JTextArea						pakTxt			= new JTextArea("Select a File to output to ...");
	private JButton							dirBtn			= new JButton("...");
	private JButton							pakBtn			= new JButton("...");
	private boolean							pakSelected		= false;
	private boolean							dirSelected		= false;
	private JButton							startBtn		= new JButton("Start!");
	private JButton							packInfoBtn		= new JButton("Pack Info...");
	private JTextPane						messageTextArea	= new JTextPane();
	private JProgressBar					jProgBar		= new JProgressBar(0, 100);
	// List of crc64 and offset
	private List<SimpleEntry<Long, Long>>	crcLookupTable;
	private int								numAdded		= 0;

	public Archiver(BufferedWriter logFile) throws IOException {
		this.logFile = logFile;
		this.setMinimumSize(screenDimension);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		Container c = this.getContentPane();
		c.setLayout(new RiverLayout());

		JScrollPane p = new JScrollPane(messageTextArea);
		p.setAutoscrolls(true);
		p.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			public void adjustmentValueChanged(AdjustmentEvent e) {
				e.getAdjustable().setValue(e.getAdjustable().getMaximum());
			}
		});
		this.setTitle("Rice Tex Packer for Wii64 " + version + " by emu_kidid, sepp256 & tehpola");
		c.add("center", new JLabel("Rice Tex Packer for Wii64 " + version + " by emu_kidid, sepp256 & tehpola"));
		c.add("p left", new JLabel("Tex Dir:"));
		c.add("tab", dirTxt);
		c.add("tab", dirBtn);
		c.add("br", new JLabel("Pak Output:"));
		c.add("tab", pakTxt);
		c.add("tab", pakBtn);
		c.add("br vtop", new JLabel("Messages"));
		c.add("tab hfill vfill", p);
		c.add("p hfill", jProgBar);
		c.add("p center", startBtn);
		c.add("p right", packInfoBtn);

		startBtn.setEnabled(false);
		startBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				setStartPushed(true);
			}

		});
		pakBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser jfc = new JFileChooser();
				jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				if (jfc.showSaveDialog(getContentPane()) == JFileChooser.APPROVE_OPTION) {
					pakTxt.setText(jfc.getSelectedFile().getAbsolutePath());
					pakSelected = true;
					startBtn.setEnabled(dirSelected && pakSelected);
				}
			}
		});
		dirBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser jfc = new JFileChooser();
				jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (jfc.showOpenDialog(getContentPane()) == JFileChooser.APPROVE_OPTION) {
					dirTxt.setText(jfc.getSelectedFile().getAbsolutePath());
					dirSelected = true;
					startBtn.setEnabled(dirSelected && pakSelected);
				}
			}
		});
		packInfoBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JDialog dlg = new JDialog();
				JTextArea header = new JTextArea(
						"u8 magic[4]; //'GXA1'\nu32 table_location; // LUT location in the pack\nu32 num_entries; // Number of entries in the file");
				JTextArea compressedEntry = new JTextArea("u64 crc structure made up of the following:\n"
						+ "	unsigned int  crc32;\n" + "	unsigned char pal_crc32_byte1;\n"
						+ "	unsigned char pal_crc32_byte2;\n" + "	unsigned char pal_crc32_byte3;\n"
						+ "	unsigned      format : 4;\n" + "	unsigned      size   : 4;\n" + "u32 offset");
				JTextArea lutEntry = new JTextArea(
						"u8 width div 4;\nu8 height div 4;\nu8 gx_fmt;\nu8 gxTex[compressedSize]");
				header.setEditable(false);
				compressedEntry.setEditable(false);
				lutEntry.setEditable(false);
				dlg.setModal(true);
				dlg.setMinimumSize(new Dimension(750, 400));
				dlg.setPreferredSize(new Dimension(750, 400));
				dlg.setLayout(new RiverLayout());
				dlg.add("center", new JLabel("Rice Tex Packer Pack Info"));
				dlg.add("p left", new JLabel("Header:"));
				dlg.add("tab hfill", header);
				dlg.add("p left", new JLabel("Compressed Entry:"));
				dlg.add("tab hfill", compressedEntry);
				dlg.add("p left", new JLabel("LUT Entry:"));
				dlg.add("tab hfill", lutEntry);
				dlg.setVisible(true);
			}
		});
	}

	public void run() {
		outputArchive = new File(getPakPath());
		try {
			jProgBar.setValue(0);
			numAdded = 0;
			crcLookupTable = new ArrayList<SimpleEntry<Long, Long>>();
			textureFiles = new ArrayList<File>();
			fw = new RandomAccessFile(outputArchive, "rw");
			initializePak();
			getTextureFiles(new File(getTexturePath()));
			if (processTextures()) {
				finalizePak();
				printStats();
				JOptionPane.showMessageDialog(dirTxt, "Successfully created texture pak!");
			} else {
				JOptionPane.showMessageDialog(dirTxt, "Failed to create texture pak!");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Performs texture file priority based on type (_all,_rgb,_a, etc) and
	 * processes/adds files
	 */
	private boolean processTextures() {
		// Don't keep redundant files based on a hierarchy
		List<File> tmp = new ArrayList<File>();
		for (int i = 0; i < textureFiles.size(); i++) {
			String curFileName = textureFiles.get(i).getName();
			if (StringUtils.endsWithIgnoreCase(curFileName, "_all.png")) {
				// If we have an _all.png file, we want it
				tmp.add(textureFiles.get(i));
			}
		}
		// Look to see if we have any _rgb.png or _a.png files, we want these
		// only if the _all.png doesn't exist with the same name
		for (int i = 0; i < textureFiles.size(); i++) {
			String curFileName = textureFiles.get(i).getName();
			if (StringUtils.endsWithIgnoreCase(curFileName, "_rgb.png")
					|| StringUtils.endsWithIgnoreCase(curFileName, "_a.png")) {
				boolean found = false;
				for (int j = 0; j < tmp.size(); j++) {
					if (StringUtils.endsWithIgnoreCase(tmp.get(j).getName(), "_all.png")) {
						String s1 = StringUtils.substringBeforeLast(curFileName, "_");
						String s2 = StringUtils.substringBeforeLast(tmp.get(j).getName(), "_");
						if (StringUtils.equalsIgnoreCase(s1, s2)) {
							found = true;
							break;
						}
					}
				}
				if (!found) {
					tmp.add(textureFiles.get(i));
				}
			}
		}
		// TODO: just keep _ciByRGBA.png type for now ??
		for (int i = 0; i < textureFiles.size(); i++) {
			if (StringUtils.endsWithIgnoreCase(textureFiles.get(i).getName(), "_ciByRGBA.png")) {
				tmp.add(textureFiles.get(i));
			}
		}
		textureFiles.clear();
		textureFiles.addAll(tmp);
		tmp.clear();

		// Add them all now
		for (int i = 0; i < textureFiles.size(); i++) {
			File primaryFile = textureFiles.get(i);
			File alphaFile = null;
			// Pair alpha with _rgb files (if they exist)
			if (!StringUtils.endsWithIgnoreCase(primaryFile.getName(), "_a.png")) {
				if (StringUtils.endsWithIgnoreCase(primaryFile.getName(), "_rgb.png")) {
					for (int j = 0; j < textureFiles.size(); j++) {
						if (StringUtils.endsWithIgnoreCase(textureFiles.get(j).getName(), "_a.png")) {
							String s1 = StringUtils.substringBeforeLast(primaryFile.getName(), "_") + "_a.png";
							String s2 = textureFiles.get(j).getName();
							if (StringUtils.equalsIgnoreCase(s1, s2)) {
								alphaFile = textureFiles.get(j);
							}
						}
					}
				}
				ArchiveEntry newEntry = new ArchiveEntry(primaryFile, alphaFile);
				if (newEntry.process()) {
					addEntry(newEntry);
				} else {
					log("\r\nERROR: \r\n" + newEntry.getMeta().getFileName() + "\r\nFailed to process due to: "
							+ newEntry.getMeta().getErrorMsg());
					return false;
				}
				newEntry = null;
				primaryFile = null;
				alphaFile = null;
				jProgBar.setValue((int) (Math.ceil(((float) i / (float) textureFiles.size()) * 100)));
				jProgBar.setStringPainted(true);
				jProgBar.setString(i + 1 + " of " + textureFiles.size() + " processed");
				jProgBar.repaint();
			}
		}
		return true;
	}

	/** Gets all the files which might be textures */
	private void getTextureFiles(File file) {
		jProgBar.setStringPainted(true);
		jProgBar.setIndeterminate(true);
		if (file.isFile() && StringUtils.endsWith(file.getAbsolutePath(), ".png")
				&& StringUtils.contains(file.getAbsolutePath(), "#") && file.length() > 0) {
			// A possible texture!
			textureFiles.add(file);
			jProgBar.setString(textureFiles.size() + " possible textures found");
			jProgBar.repaint();
			messageTextArea.setText("Number of image files found: " + textureFiles.size());
		} else {
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					getTextureFiles(f);
				}
			}
		}
		jProgBar.setIndeterminate(false);
	}

	/** Initialize the pak by writing the header and skipping to 0x20 */
	private void initializePak() throws IOException {
		String magic = "GXA1";
		fw.write(magic.getBytes());
		fw.seek((long) 0x0C);
	}

	/**
	 * Sort the CRC table by lowest to highest CRC, update the header and
	 * finally write the table
	 */
	private void finalizePak() throws IOException {
		Collections.sort(crcLookupTable, new Comparator<SimpleEntry<Long, Long>>() {

			@Override
			public int compare(SimpleEntry<Long, Long> o1, SimpleEntry<Long, Long> o2) {
				return (new BigInteger(1, longToBytes(o1.getKey()))).compareTo(new BigInteger(1, longToBytes(o2
						.getKey())));
			}
		});
		long tableLoc = fw.getFilePointer();
		fw.seek((long) 0x04);
		fw.writeInt((int) tableLoc);
		fw.writeInt(crcLookupTable.size());
		fw.seek(tableLoc);
		for (Map.Entry<Long, Long> entry : crcLookupTable) {
			fw.writeLong(entry.getKey().longValue());
			fw.writeInt(entry.getValue().intValue());
		}
		fw.close();
	}

	/** Adds an entry to the zipped stream and updates the LUT */
	public void addEntry(ArchiveEntry entry) {
		try {
			// Update the Entry Meta with our current File Pointer
			entry.getMeta().setOffset(fw.getFilePointer());

			// Add it to our LUT
			crcLookupTable.add(new SimpleEntry<Long, Long>(entry.getMeta().getCRC64(), entry.getMeta().getOffset()));

			// Write out the Actual Entry (Width+Height+GXTexture)
			fw.writeByte(new Integer(entry.getMeta().getWidth() / 4).byteValue());
			fw.writeByte(new Integer(entry.getMeta().getHeight() / 4).byteValue());
			fw.writeByte(entry.getMeta().getGXFormat().byteValue());
			fw.write(entry.getGXTexture());

			// Update the log window
			log(entry.getMeta().toString());
			numAdded++;
		} catch (IOException e) {
			e.printStackTrace();
			log("Failed to add entry to pak! " + entry.getMeta().toString());
		}
	}

	public void printStats() {
		log("Entries added: " + numAdded);
	}

	/**
	 * @return The root path of all of the individual .png files
	 */
	public String getTexturePath() {
		return dirTxt.getText();
	}

	/**
	 * @return The path of the compressed and final pak path
	 */
	public String getPakPath() {
		return pakTxt.getText();
	}

	/**
	 * Logs msg to the screen text area
	 * 
	 * @param msg
	 */
	private void log(String msg) {
		if (StringUtils.countMatches(messageTextArea.getText(), "\r\n") > 1000) {
			messageTextArea.setText(msg); // after 1000 lines, clear it.
		} else {
			messageTextArea.setText(messageTextArea.getText() + "\r\n" + msg);
		}
		try {
			logFile.write(msg + "\r\n");
		} catch (IOException e) {
			e.printStackTrace(); // BAD
		}
	}

	public final byte[] longToBytes(long v) {
		byte[] writeBuffer = new byte[8];

		writeBuffer[0] = (byte) (v >>> 56);
		writeBuffer[1] = (byte) (v >>> 48);
		writeBuffer[2] = (byte) (v >>> 40);
		writeBuffer[3] = (byte) (v >>> 32);
		writeBuffer[4] = (byte) (v >>> 24);
		writeBuffer[5] = (byte) (v >>> 16);
		writeBuffer[6] = (byte) (v >>> 8);
		writeBuffer[7] = (byte) (v >>> 0);

		return writeBuffer;
	}

	public boolean isStartPushed() {
		return startPushed;
	}

	public void setStartPushed(boolean startPushed) {
		this.startPushed = startPushed;
	}

}
