package gov.usgs.volcanoes.swarm.heli;

import gov.usgs.volcanoes.core.configfile.ConfigFile;

import gov.usgs.volcanoes.core.contrib.PngEncoder;
import gov.usgs.volcanoes.core.contrib.PngEncoderB;
import gov.usgs.volcanoes.core.data.HelicorderData;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.data.file.FileType;
import gov.usgs.volcanoes.core.legacy.plot.Plot;
import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.legacy.plot.render.HelicorderRenderer;
import gov.usgs.volcanoes.core.time.Ew;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.ui.ExtensionFileFilter;
import gov.usgs.volcanoes.core.ui.GridBagHelper;
import gov.usgs.volcanoes.core.util.UiUtils;
import gov.usgs.volcanoes.swarm.Icons;
import gov.usgs.volcanoes.swarm.Kioskable;
import gov.usgs.volcanoes.swarm.Swarm;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.SwarmFrame;
import gov.usgs.volcanoes.swarm.SwarmUtil;
import gov.usgs.volcanoes.swarm.SwingWorker;
import gov.usgs.volcanoes.swarm.Throbber;
import gov.usgs.volcanoes.swarm.chooser.DataChooser;
import gov.usgs.volcanoes.swarm.data.FileDataSource;
import gov.usgs.volcanoes.swarm.data.GulperListener;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;
import gov.usgs.volcanoes.swarm.data.SeismicDataSourceListener;
import gov.usgs.volcanoes.swarm.internalFrame.SwarmInternalFrames;
import gov.usgs.volcanoes.swarm.map.MapFrame;
import gov.usgs.volcanoes.swarm.time.TimeListener;
import gov.usgs.volcanoes.swarm.time.UiTime;
import gov.usgs.volcanoes.swarm.time.WaveViewTime;
import gov.usgs.volcanoes.swarm.wave.StatusTextArea;
import gov.usgs.volcanoes.swarm.wave.WaveClipboardFrame;
import gov.usgs.volcanoes.swarm.wave.WaveViewSettings;
import gov.usgs.volcanoes.swarm.wave.WaveViewSettingsToolbar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import org.apache.poi.xssf.usermodel.*;
import org.apache.commons.math3.util.Pair;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DateUtil;


/**
 * <code>JInternalFrame</code> that holds a helicorder.
 *
 * @author Dan Cervelli
 */
public class HelicorderViewerFrame extends SwarmFrame implements Kioskable {
  public static final long serialVersionUID = -1;

  private static final int MINUTE = 60;
  private static final int HOUR = MINUTE * 60;
  
  // minutes * 60 = seconds
  public static final int[] chunkValues =
      new int[] {10 * MINUTE, 15 * MINUTE, 20 * MINUTE, 
          30 * MINUTE, 1 * HOUR, 2 * HOUR, 180 * MINUTE, 6 * HOUR};

  // hours * 60 = minutes
  public static final int[] spanValues =
      new int[] {2 * 60, 4 * 60, 6 * 60, 12 * 60, 24 * 60, 48 * 60, 72 * 60, 96 * 60, 120 * 60,
          144 * 60, 168 * 60, 192 * 60, 216 * 60, 240 * 60, 264 * 60, 288 * 60, 312 * 60, 336 * 60};

  // seconds
  public static final int[] zoomValues = new int[] {1, 2, 5, 10, 20, 30, 
      MINUTE, 2 * MINUTE, 5 * MINUTE, 10 * MINUTE, 20 * MINUTE, 40 * MINUTE, 1 * HOUR, 90 * MINUTE};
 
  private final RefreshThread refreshThread;
  private final SeismicDataSource dataSource;
  private JPanel mainPanel;
  private JToolBar toolBar;
  private JToggleButton pinButton;
  private JButton settingsButton;
  private JButton backButton;
  private JButton forwardButton;
  private JButton groundTruthButton;
  private JButton compX;
  private JButton expX;
  private JButton compY;
  private JButton expY;
  private JButton clipboard;
  private JToggleButton tagButton;
  private JButton removeWave;
  private JButton capture;
  private JFileChooser chooser;
  private JButton scaleButton;
  private boolean scaleClipState;
  protected JToggleButton autoScaleSliderButton;
  protected int autoScaleSliderButtonState;
  protected JSlider autoScaleSlider;

  private static HelicorderViewPanel helicorderViewPanel;

  private final WaveViewSettings waveViewSettings;
  private static HelicorderViewerSettings settings;
  
  private HelicorderViewerFrame instance;

  private boolean gulperWorking;
  private boolean working;
  private StatusTextArea statusText;

  private JPanel heliPanel;

  protected long lastRefreshTime;

  private Border border;
  private Border thinBorder;

  protected Throbber throbber;
  protected JProgressBar progressBar;

  private boolean noData = false;

  private TimeListener timeListener;

  public GulperListener gulperListener;

  private SeismicDataSourceListener dataListener;

  public File groundTruthFile;

  public String dateString;

  public static File excelFilePath;

  public static XSSFWorkbook workbook;

  public static int rowNum;

  public static int cellNum;

  public static XSSFSheet sheet;

  public static FileOutputStream outFreq;
  
  public static File outFile;
  
  public static String channelHeli;
  
  public static Boolean firstGroundOpening;

  /**
   * Constructor with configuration file as parameter.
   * @param cf configuration file
   */
  public HelicorderViewerFrame(final ConfigFile cf) {
    super("<layout>", true, true, true, true);
    UiTime.touchTime();
    final String channel = cf.getString("channel");
    dataSource = swarmConfig.getSource(cf.getString("source"));
    setTitle(channel + ", [" + dataSource + "]");
    settings = new HelicorderViewerSettings(channel);
    settings.set(cf);
    waveViewSettings = new WaveViewSettings();
    waveViewSettings.set(cf.getSubConfig("wave"));

    createUi();
    final boolean pinned = Boolean.parseBoolean(cf.getString("pinned"));
    setPinned(pinned);
    processStandardLayout(cf);
    setVisible(true);
    getHelicorder();
    refreshThread = new RefreshThread();
    instance = this;
  }

  /**
   * Constructor with data source, channel, and bottom time info as parameters.
   * @param sds seismic data source
   * @param ch channel string
   * @param bt bottom time
   */
  public HelicorderViewerFrame(final SeismicDataSource sds, final String ch, final double bt) {
    super(ch + ", [" + sds + "]", true, true, true, true);
    UiTime.touchTime();
    settings = new HelicorderViewerSettings(ch);
    settings.setBottomTime(bt);
    waveViewSettings = new WaveViewSettings();
    dataSource = sds;

    createUi();
    setVisible(true);
    getHelicorder();
    refreshThread = new RefreshThread();
  }

  /**
   * Save layout.
   * @see gov.usgs.volcanoes.swarm.SwarmFrame#saveLayout
   * (gov.usgs.volcanoes.core.configfile.ConfigFile, java.lang.String)
   */
  public void saveLayout(final ConfigFile cf, final String prefix) {
    super.saveLayout(cf, prefix);
    cf.put("helicorder", prefix);
    cf.put(prefix + ".source", dataSource.getName());
    cf.put(prefix + ".pinned", Boolean.toString(pinButton.isSelected()));
    settings.save(cf, prefix);
    waveViewSettings.save(cf, prefix + ".wave");
  }

  /**
   * Create helicorder view panel user interface.
   */
  public void createUi() {
    System.out.println("creating UI");
    workbook = new XSSFWorkbook();
    
    if (SwarmInternalFrames.heliOpened == 0)
    {
      rowNum = 1;

      sheet = workbook.createSheet("Frequency Data");
      excelFilePath = new File("Frequencies.xlsx");
      
      XSSFWorkbook workbookFreq = new XSSFWorkbook();

      //Create file system using specific name
      FileOutputStream out;
      try {
        sheet = workbookFreq.createSheet("Frequency Data");
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue("Channel");
        Cell cell2 = row.createCell(1);
        cell2.setCellValue("Date");
        Cell cell3 = row.createCell(2);
        cell3.setCellValue("Frequency");
        Cell cell4 = row.createCell(3);
        cell4.setCellValue("Power");
        out = new FileOutputStream(excelFilePath);
        workbookFreq.write(out);
        out.close();
        
      } catch (FileNotFoundException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    //write operation workbook using file out object
    
    
    mainPanel = new JPanel(new BorderLayout());
    createHeliPanel();
    createToolBar();
    createStatusText();
    createListeners();

    setFrameIcon(Icons.heli);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setSize(800, 750);
    setContentPane(mainPanel);
    
    SwarmInternalFrames.heliOpened++;
    firstGroundOpening = true;

  }

  public void addLinkListeners() {
    helicorderViewPanel.addListener(WaveClipboardFrame.getInstance().getLinkListener());
    helicorderViewPanel.addListener(MapFrame.getInstance().getLinkListener());
  }

  private void createStatusText() {
    final JPanel statusPanel = new JPanel();
    statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
    statusText = new StatusTextArea(" ");
    statusText.setBorder(BorderFactory.createEmptyBorder(1, 5, 0, 0));
    statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 3));
    statusPanel.add(statusText);
    statusPanel.add(Box.createHorizontalGlue());
    progressBar = new JProgressBar(0, 100);
    progressBar.setVisible(false);
    progressBar.setStringPainted(true);
    progressBar.setPreferredSize(new Dimension(100, 15));
    progressBar.setSize(new Dimension(100, 15));
    progressBar.setMaximumSize(new Dimension(100, 15));
    statusPanel.add(progressBar);
    mainPanel.add(statusPanel, BorderLayout.SOUTH);
  }

  private void createHeliPanel() {
    helicorderViewPanel = new HelicorderViewPanel(this);
    settings.view = helicorderViewPanel;
    heliPanel = new JPanel(new BorderLayout());
    thinBorder = LineBorder.createGrayLineBorder();
    border =
        BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 2, 0, 3), thinBorder);

    heliPanel.setBorder(border);
    heliPanel.add(helicorderViewPanel, BorderLayout.CENTER);
    mainPanel.add(heliPanel, BorderLayout.CENTER);
  }

  private void createToolBar() {
    toolBar = SwarmUtil.createToolBar();

    pinButton = SwarmUtil.createToolBarToggleButton(Icons.pin, "Helicorder always on top",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            setPinned(pinButton.isSelected());
          }
        });
    toolBar.add(pinButton);

    settingsButton = SwarmUtil.createToolBarButton(Icons.settings, "Helicorder view settings",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            final HelicorderViewerSettingsDialog hvsd =
                HelicorderViewerSettingsDialog.getInstance(settings, waveViewSettings);
            hvsd.setVisible(true);
            noData = false;
            getHelicorder();
          }
        });
    toolBar.add(settingsButton);

    toolBar.addSeparator();

    backButton = SwarmUtil.createToolBarButton(Icons.left, "Scroll back time (A or Left arrow)",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            if (helicorderViewPanel.hasInset()) {
              helicorderViewPanel.moveInset(-1);
            } else {
              scroll(-1);
            }
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "LEFT", "backward1", backButton);
    UiUtils.mapKeyStrokeToButton(this, "A", "backward2", backButton);
    toolBar.add(backButton);

    forwardButton = SwarmUtil.createToolBarButton(Icons.right,
        "Scroll forward time (Z or Right arrow)", new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            if (helicorderViewPanel.hasInset()) {
              helicorderViewPanel.moveInset(1);
            } else {
              scroll(1);
            }
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "RIGHT", "forward1", forwardButton);
    UiUtils.mapKeyStrokeToButton(this, "Z", "forward2", forwardButton);
    toolBar.add(forwardButton);

    compX = SwarmUtil.createToolBarButton(Icons.xminus, "Compress X-axis (Alt-left arrow)",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            decXAxis();
            getHelicorder();
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "alt LEFT", "compx", compX);
    toolBar.add(compX);

    expX = SwarmUtil.createToolBarButton(Icons.xplus, "Expand X-axis (Alt-right arrow)",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            incXAxis();
            getHelicorder();
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "alt RIGHT", "expx", expX);
    toolBar.add(expX);

    compY = SwarmUtil.createToolBarButton(Icons.yminus, "Compress Y-axis (Alt-down arrow)",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            decYAxis();
            getHelicorder();
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "alt DOWN", "compy", compY);
    toolBar.add(compY);

    expY = SwarmUtil.createToolBarButton(Icons.yplus, "Expand Y-axis (Alt-up arrow)",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            incYAxis();
            getHelicorder();
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "alt UP", "expy", expY);
    toolBar.add(expY);

    toolBar.addSeparator();

    final JButton addZoom = SwarmUtil.createToolBarButton(Icons.zoomplus,
        "Decrease zoom time window (+)", new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            decZoom();
            settings.notifyView();
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "EQUALS", "addzoom1", addZoom);
    UiUtils.mapKeyStrokeToButton(this, "shift EQUALS", "addzoom2", addZoom);
    toolBar.add(addZoom);

    final JButton subZoom = SwarmUtil.createToolBarButton(Icons.zoomminus,
        "Increase zoom time window (-)", new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            incZoom();
            settings.notifyView();
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "MINUS", "subzoom", subZoom);
    toolBar.add(subZoom);

    new WaveViewSettingsToolbar(waveViewSettings, toolBar, this);
    
    clipboard = SwarmUtil.createToolBarButton(Icons.clipboard,
        "Copy inset to clipboard (C or Ctrl-C)", new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            helicorderViewPanel.insetToClipboard();
          }
        });
    clipboard.setEnabled(false);
    UiUtils.mapKeyStrokeToButton(this, "control C", "clipboard1", clipboard);
    UiUtils.mapKeyStrokeToButton(this, "C", "clipboard2", clipboard);
    toolBar.add(clipboard);

    // picker = SwarmUtil.createToolBarButton(Icons.ruler,
    // "Copy inset to picker (P)", new ActionListener() {
    // public void actionPerformed(final ActionEvent e) {
    // helicorderViewPanel.insetToPicker();
    // }
    // });
    // picker.setEnabled(false);
    // UiUtils.mapKeyStrokeToButton(this, "control P", "picker1", picker);
    // UiUtils.mapKeyStrokeToButton(this, "P", "picker2", picker);
    // toolBar.add(picker);

    removeWave = SwarmUtil.createToolBarButton(Icons.delete, "Remove inset wave (Delete or Escape)",
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            helicorderViewPanel.removeWaveInset();
          }
        });
    removeWave.setEnabled(false);
    UiUtils.mapKeyStrokeToButton(this, "ESCAPE", "removewave", removeWave);
    UiUtils.mapKeyStrokeToButton(this, "DELETE", "removewave", removeWave);
    toolBar.add(removeWave);

    toolBar.addSeparator();

    capture = SwarmUtil.createToolBarButton(Icons.camera, "Save helicorder image (P)",
        new CaptureActionListener());
    UiUtils.mapKeyStrokeToButton(this, "P", "capture", capture);
    toolBar.add(capture);

    toolBar.addSeparator();

    tagButton = SwarmUtil.createToolBarToggleButton(Icons.tag,
        "Tag Mode", null);
    tagButton.setEnabled(true);
    toolBar.add(tagButton);

    tagButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (tagButton.isSelected()) {
          helicorderViewPanel.getTagMenu().browseForEventFileName();
          helicorderViewPanel.makeInsetPanelTagEnabled();
        } else {
          helicorderViewPanel.tagData.clear();
        }
        repaint();
      }
    });

    toolBar.addSeparator();
    
    //Begin addition

    groundTruthButton = SwarmUtil.createToolBarButton(Icons.eye,
        "Ground Truth (T)", new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            
            FileDataSource fds = FileDataSource.getInstance();
            JFileChooser chooser = new JFileChooser();
            chooser.resetChoosableFileFilters();
            for (FileType ft : FileType.getKnownTypes()) {
              ExtensionFileFilter f = new ExtensionFileFilter(ft.extension, ft.description);
              chooser.addChoosableFileFilter(f);
            }

            chooser.setFileFilter(chooser.getAcceptAllFileFilter());
            File lastPath = new File(SwarmConfig.getInstance().lastPath);
            chooser.setCurrentDirectory(lastPath);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(true);
            chooser.setDialogTitle("Choose Ground File");
            int result = chooser.showOpenDialog(Swarm.getApplicationFrame());
            
            if (result == JFileChooser.APPROVE_OPTION) {
              File[] fs = chooser.getSelectedFiles();
              try {
                groundTruthFile = fs[0];
                acceptGroundTruth(groundTruthFile);
              } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              }
              
            }
            
            
          }
        });
    UiUtils.mapKeyStrokeToButton(this, "T", "showground", groundTruthButton);
    toolBar.add(groundTruthButton);
    
     
    
    //End addition
    
    scaleButton = SwarmUtil.createToolBarButton(Icons.wavezoom,
        "Toggle between adjusting helicoder scale and clip", new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            scaleClipState = !scaleClipState;
            if (scaleClipState) {
              autoScaleSlider.setValue(40 - (new Double(settings.barMult * 4).intValue()));
              scaleButton.setIcon(Icons.waveclip);
              autoScaleSlider.setToolTipText("Adjust helicorder clip");
            } else {
              autoScaleSlider.setValue(settings.clipBars / 3);
              scaleButton.setIcon(Icons.wavezoom);
              autoScaleSlider.setToolTipText("Adjust helicorder scale");
            }

            settings.notifyView();
          }
        });
    scaleButton.setSelected(true);
    toolBar.add(scaleButton);

    autoScaleSlider = new JSlider(1, 39, (int) ((10 - settings.barMult) * 4));
    autoScaleSlider.setToolTipText("Adjust helicorder scale");
    autoScaleSlider.setFocusable(false);
    autoScaleSlider.setPreferredSize(new Dimension(80, 20));
    autoScaleSlider.setMaximumSize(new Dimension(80, 20));
    autoScaleSlider.setMinimumSize(new Dimension(80, 20));
    autoScaleSlider.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        settings.autoScale = true;
        if (!autoScaleSlider.getValueIsAdjusting()) {
          if (scaleClipState) {
            settings.clipBars = autoScaleSlider.getValue() * 3;
          } else {
            settings.barMult = 10 - (new Integer(autoScaleSlider.getValue()).doubleValue() / 4);
          }
          repaintHelicorder();
        }
      }
    });
    toolBar.add(autoScaleSlider);

    toolBar.add(Box.createHorizontalGlue());
    throbber = new Throbber();
    toolBar.add(throbber);
    
    
    
    mainPanel.add(toolBar, BorderLayout.NORTH);
  }
  
  private void createListeners() {
    timeListener = new TimeListener() {
      public void timeChanged(final double j2k) {
        helicorderViewPanel.setCursorMark(j2k);
      }
    };
    WaveViewTime.addTimeListener(timeListener);

    this.addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameActivated(final InternalFrameEvent e) {
        if (settings.channel != null) {
          DataChooser.getInstance().setNearest(settings.channel);
        }
      }

      @Override
      public void internalFrameClosing(final InternalFrameEvent e) {
        
        dispose();
        throbber.close();
        refreshThread.kill();
        SwarmInternalFrames.remove(HelicorderViewerFrame.this);
        WaveViewTime.removeTimeListener(timeListener);
        dataSource.notifyDataNotNeeded(settings.channel, helicorderViewPanel.getStartTime(),
            helicorderViewPanel.getEndTime(), gulperListener);
        dataSource.close();
        
        
        if (rowNum > 0)  //data has been added into the excel file
        {
          JOptionPane option = new JOptionPane();
          
          int reply = JOptionPane.showConfirmDialog(new JInternalFrame(), "Would you like to save the current frequencies/powers excel file?", "Saving",  JOptionPane.YES_NO_OPTION);
          if (reply == JOptionPane.YES_OPTION)
          {

            String inputValue = JOptionPane.showInputDialog(new JInternalFrame(), 
                "Enter a name for the excel file (minus the file extension):", "Save As", JOptionPane.QUESTION_MESSAGE);
            
            String saveAsValue = inputValue + ".xlsx";

            try {
              FileInputStream inputStream = new FileInputStream(new File(HelicorderViewerFrame.excelFilePath.getName()));

              Workbook workbook = WorkbookFactory.create(inputStream);
              
              FileOutputStream outputStream = new FileOutputStream(saveAsValue);
              workbook.write(outputStream);
              workbook.close();
              outputStream.close();
               
            } catch (IOException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            } 
            
          }
        }
        
        SwarmInternalFrames.heliOpened--;
        if (SwarmInternalFrames.heliOpened == 0)
        {
          excelFilePath.delete();
        }

      }

      @Override
      public void internalFrameDeiconified(final InternalFrameEvent e) {
        helicorderViewPanel.setResized(true);
        repaintHelicorder();
        repaint();
      }
    });

    this.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        if (getWidth() < 530) {
          helicorderViewPanel.setMinimal(true);
        } else {
          helicorderViewPanel.setMinimal(false);
        }

        helicorderViewPanel.setResized(true);
        repaintHelicorder();
        repaint();
      }
    });

    gulperListener = new GulperListener() {
      public void gulperStarted() {
        gulperWorking = true;
        throbber.increment();
      }

      public void gulperStopped(final boolean killed) {
        if (killed) {
          noData = true;
        } else {
          gulperWorking = false;
          throbber.decrement();
          final HelicorderData hd = helicorderViewPanel.getData();
          if (hd == null || hd.rows() == 0) {
            noData = true;
          }
          repaintHelicorder();
        }
      }

      public void gulperGulped(final double t1, final double t2, final boolean success) {
        if (success) {
          getHelicorder();
        }
      }
    };

    dataListener = new SeismicDataSourceListener() {

      public void channelsProgress(final String id, final double progress) {}

      public void channelsUpdated() {}

      public void helicorderProgress(final String channel, final double progress) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (!progressBar.isVisible()) {
              progressBar.setVisible(true);
            }

            if (progress == -1) {
              progressBar.setIndeterminate(true);
              progressBar.setString("Waiting for server");
            } else if (progress >= 0.0 && progress < 1.0) {
              progressBar.setIndeterminate(false);
              progressBar.setValue((int) (progress * 100));
              progressBar.setString("Downloading");
            }

            if (progress == 1.0) {
              progressBar.setVisible(false);
            }
          }
        });

      }
    };
    dataSource.addListener(dataListener);
  }

  public static HelicorderViewPanel getHelicorderViewPanel() {
    return helicorderViewPanel;
  }

  /**
   * Set pinned flag.
   * @param b true if pinned; false otherwise
   */
  public void setPinned(final boolean b) {
    pinButton.setSelected(b);
    final int layer = b ? JLayeredPane.MODAL_LAYER : JLayeredPane.DEFAULT_LAYER;
    Swarm.setFrameLayer(HelicorderViewerFrame.this, layer);
  }

  /**
   * Expand X-axis.
   */
  public void incXAxis() {
    final int index = SwarmUtil.linearSearch(chunkValues, settings.timeChunk);
    if (index == -1 || index == chunkValues.length - 1) {
      return;
    }
    settings.timeChunk = chunkValues[index + 1];
  }

  /**
   * Compress X-axis.
   */
  public void decXAxis() {
    final int index = SwarmUtil.linearSearch(chunkValues, settings.timeChunk);
    if (index == -1 || index == 0) {
      return;
    }
    settings.timeChunk = chunkValues[index - 1];
  }

  /**
   * Expand Y-axis.
   */
  public void incYAxis() {
    final int index = SwarmUtil.linearSearch(spanValues, settings.span);
    if (index == -1 || index == spanValues.length - 1) {
      return;
    }
    settings.span = spanValues[index + 1];
  }

  /**
   * Compress Y-axis.
   */
  public void decYAxis() {
    final int index = SwarmUtil.linearSearch(spanValues, settings.span);
    if (index == -1 || index == 0) {
      return;
    }
    settings.span = spanValues[index - 1];
  }

  /**
   * Increase zoom window.
   */
  public void incZoom() {
    final int index = SwarmUtil.linearSearch(zoomValues, settings.waveZoomOffset);
    if (index == -1 || index == zoomValues.length - 1) {
      return;
    }
    settings.waveZoomOffset = zoomValues[index + 1];
  }

  /**
   * Decrease zoom window.
   */
  public void decZoom() {
    final int index = SwarmUtil.linearSearch(zoomValues, settings.waveZoomOffset);
    if (index == -1 || index == 0) {
      return;
    }
    settings.waveZoomOffset = zoomValues[index - 1];
  }

  /**
   * Enable or disable inset buttons.
   * @param b true to enable; false to disable
   */
  public void setInsetButtonsEnabled(final boolean b) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        clipboard.setEnabled(b);
        removeWave.setEnabled(b);
        // picker.setEnabled(b);
      }
    });
  }

  /**
   * Enable or disable navigation buttons.
   * @param b true to enable; false to disable
   */
  public void setNavigationButtonsEnabled(final boolean b) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        compX.setEnabled(b);
        expX.setEnabled(b);
        forwardButton.setEnabled(b);
        backButton.setEnabled(b);
        compY.setEnabled(b);
        expY.setEnabled(b);
        capture.setEnabled(b);
      }
    });
  }

  /**
   * @see gov.usgs.volcanoes.swarm.Kioskable#setKioskMode(boolean)
   */
  public void setKioskMode(final boolean b) {
    super.setDefaultKioskMode(b);
    helicorderViewPanel.setFullScreen(fullScreen);
    if (fullScreen) {
      mainPanel.remove(toolBar);
      heliPanel.setBorder(null);
    } else {
      mainPanel.add(toolBar, BorderLayout.NORTH);
      heliPanel.setBorder(border);
    }
    helicorderViewPanel.requestFocus();
  }

  /**
   * Set status bar string.
   * @param status status text
   */
  public void setStatus(final String status) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        statusText.setText(status);
      }
    });
  }

  public Throbber getThrobber() {
    return throbber;
  }

  public StatusTextArea getStatusText() {
    return statusText;
  }

  public WaveViewSettings getWaveViewSettings() {
    return waveViewSettings;
  }

  public static HelicorderViewerSettings getHelicorderViewerSettings() {
    return settings;
  }

  public void repaintHelicorder() {
    helicorderViewPanel.invalidateImage();
  }

  /**
   * Scroll forward or backward in time.
   * @param units positive time units to go forward; negative to go backwards
   */
  public void scroll(final int units) {
    double bt = settings.getBottomTime();
    if (Double.isNaN(bt)) {
      bt = J2kSec.now();
    }

    settings.setBottomTime(bt + units * settings.scrollSize * settings.timeChunk);
    getHelicorder();
  }

  public boolean isWorking() {
    return working || gulperWorking;
  }

  /**
   * Get and draw helicorder. 
   */
  public void getHelicorder() {
    if (noData) {
      return;
    }
    final SwingWorker worker = new SwingWorker() {
      private double end;
      private double before;
      private HelicorderData hd;
      private boolean success = false;

      @Override
      public Object construct() {
        try {
          setNavigationButtonsEnabled(false);
          throbber.increment();
          working = true;
          end = settings.getBottomTime();
          if (Double.isNaN(end)) {
            end = J2kSec.now();
          }

          before = end - settings.span * 60;
          int tc = 30;
          if (helicorderViewPanel != null) {
            tc = settings.timeChunk;
          }
          
          channelHeli = settings.channel;
//          System.out.println("channel Heli " + channelHeli);

          if (!HelicorderViewerFrame.this.isClosed) {
            hd = dataSource.getHelicorder(settings.channel.replace(' ', '$'), before - tc, end + tc,
                gulperListener);
            success = true;
          } else {
            success = false;
          }
        } catch (final Throwable e) {
          e.printStackTrace();
          System.err.println("getHelicorder() Error: " + e.getMessage());
        } finally {
          working = false;
        }
        return null;
      }

      @Override
      public void finished() {
        lastRefreshTime = System.currentTimeMillis();
        throbber.decrement();
        setNavigationButtonsEnabled(true);
        if (success) {
          if (hd != null && hd.getEndTime() < before && !dataSource.isActiveSource()) {
            // this would get executed if the data source
            // forcibly returned a different time than asked
            // for -- like in the case of a miniSEED.
            final double dt = end - before;
            before = hd.getEndTime() - dt / 2;
            end = hd.getEndTime() + dt / 2;
            settings.setBottomTime(end);
          }
          helicorderViewPanel.setHelicorder(hd, before, end);
          repaintHelicorder();
        }
      }
    };
    worker.start();
  }

  public Wave getWave(final double t1, final double t2) {
    return dataSource.getWave(settings.channel.replace(' ', '$'), t1, t2);
  }

  public SeismicDataSource getDataSource() {
    return dataSource;
  }

  private class RefreshThread extends Thread {
    private boolean kill = false;

    public RefreshThread() {
      super("HeliRefresh-" + settings.channel);
      this.setPriority(Thread.MIN_PRIORITY);
      start();
    }

    public void kill() {
      kill = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!kill) {
        // enforce a dataSource-specific minimum refresh interval
        int refreshInterval;
        if (settings.refreshInterval == 0 || dataSource.getMinimumRefreshInterval() == 0) {
          refreshInterval = 0;
        } else {
          refreshInterval =
              Math.max(settings.refreshInterval, dataSource.getMinimumRefreshInterval());
        }

        final long lastUi = System.currentTimeMillis() - UiTime.getTime();
        final boolean reset = swarmConfig.isKiosk() && lastUi > 10 * 60 * 1000;
        // TODO: extract magic number
        if (reset || !Double.isNaN(settings.getBottomTime())
            && settings.getLastBottomTimeSet() > 10 * 60 * 1000) {
          helicorderViewPanel.removeWaveInset();
          helicorderViewPanel.clearMarks();
          settings.setBottomTime(Double.NaN);
          if (swarmConfig.isKiosk() && !Swarm.isFullScreenMode()) {
            ((Swarm)Swarm.getApplicationFrame()).toggleFullScreenMode();
          }
        }

        try {
          final long now = System.currentTimeMillis();
          final long sleepTime = Math.min(now - lastRefreshTime, refreshInterval * 1000);

          if (refreshInterval > 0) {
            Thread.sleep(sleepTime);
          } else {
            Thread.sleep(30 * 1000);
          }
        } catch (final Exception e) {
          //
        }

        final long now = System.currentTimeMillis();

        if (!kill && refreshInterval > 0 && (now - lastRefreshTime) > refreshInterval * 1000) {
          try {
            final double bt = settings.getBottomTime();
            if (dataSource.isActiveSource() && Double.isNaN(bt)) {
              if (!working) {
                getHelicorder();
              }
            }
          } catch (final Exception e) {
            System.err.println("Exception during refresh:");
            e.printStackTrace();
          }
        }
      }
    }
  }

  // TODO: refactor out some functions
  private class CaptureActionListener implements ActionListener {
    public void actionPerformed(final ActionEvent e) {
      chooser = new JFileChooser();
      chooser.setDialogTitle("Save Helicorder Screen Capture");
      chooser.setSelectedFile(new File("heli.png"));
      final File lastPath = new File(swarmConfig.lastPath);
      chooser.setCurrentDirectory(lastPath);

      final JPanel imagePanel = new JPanel(new GridBagLayout());
      final GridBagConstraints c = new GridBagConstraints();
      imagePanel.setBorder(new TitledBorder(new EtchedBorder(), "Image Properties"));

      final JLabel heightLabel = new JLabel("Height:");
      final JTextField heightTextField = new JTextField(4);
      heightLabel.setLabelFor(heightTextField);
      heightTextField.setText("700");

      final JLabel widthLabel = new JLabel("Width:");
      final JTextField widthTextField = new JTextField(4);
      widthLabel.setLabelFor(widthTextField);
      widthTextField.setText("900");

      final JCheckBox includeChannel = new JCheckBox("Include channel");
      includeChannel.setSelected(true);

      final JLabel fileFormatLabel = new JLabel("File format:");
      final JComboBox<String> fileFormatCb = new JComboBox<String>();
      fileFormatCb.addItem("PNG");
      fileFormatCb.addItem("PS");

      fileFormatCb.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final JComboBox<?> source = (JComboBox<?>) e.getSource();
          if (source.getSelectedItem().equals("PS")) {
            final String fn = chooser.getSelectedFile().getName().replaceAll("\\..*$", ".ps");
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory().getAbsoluteFile(), fn));
          } else {
            final String fn = chooser.getSelectedFile().getName().replaceAll("\\..*$", ".png");
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory().getAbsoluteFile(), fn));
          }
        }
      });

      imagePanel.add(heightLabel,
          GridBagHelper.set(c, "x=0;y=0;w=1;h=1;wx=1;wy=0;ix=12;iy=2;a=nw;f=n;i=0,4,0,4"));
      imagePanel.add(heightTextField,
          GridBagHelper.set(c, "x=1;y=0;w=1;h=1;wx=1;ix=12;iy=2;f=n;i=0,4,0,4"));
      imagePanel.add(widthLabel,
          GridBagHelper.set(c, "x=0;y=1;w=1;h=1;wx=1;wy=0;ix=12;iy=2;f=n;i=0,4,0,4"));
      imagePanel.add(widthTextField,
          GridBagHelper.set(c, "x=1;y=1;w=1;h=1;wx=1;ix=12;iy=2;f=n;i=0,4,0,4"));
      imagePanel.add(fileFormatLabel,
          GridBagHelper.set(c, "x=0;y=2;w=1;h=1;wx=1;wy=0;ix=12;iy=2;a=nw;f=w;i=0,4,0,4"));
      imagePanel.add(fileFormatCb,
          GridBagHelper.set(c, "x=1;y=2;w=1;h=1;wx=1;wy=1;ix=12;iy=2;a=nw;f=w;i=0,4,0,4"));
      imagePanel.add(includeChannel,
          GridBagHelper.set(c, "x=0;y=3;w=2;h=1;wx=1;wy=1;ix=12;iy=2;a=nw;f=w;i=0,4,0,4"));

      chooser.setAccessory(imagePanel);

      final String fn = settings.channel.replace(' ', '_') + ".png";
      chooser.setSelectedFile(new File(chooser.getCurrentDirectory().getAbsoluteFile(), fn));

      final int result = chooser.showSaveDialog(Swarm.getApplicationFrame());
      if (result == JFileChooser.APPROVE_OPTION) {
        final File f = chooser.getSelectedFile();

        if (f.exists()) {
          final int choice = JOptionPane.showConfirmDialog(Swarm.getApplicationFrame(),
              "File exists, overwrite?", "Confirm", JOptionPane.YES_NO_OPTION);
          if (choice != JOptionPane.YES_OPTION) {
            return;
          }
        }

        // Render helicorder
        if (tagButton.isSelected()) { // if tag mode enabled (image displays exactly as in Swarm)
          int height = helicorderViewPanel.getHeight();
          int width = helicorderViewPanel.getWidth();

          final BufferedImage image =
              new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
          final Graphics g = image.getGraphics();
          helicorderViewPanel.paint(g);
          // draw label
          if (includeChannel.isSelected()) {
            Font oldFont = g.getFont();
            g.setFont(Font.decode("Dialog-BOLD-40"));
            String channel = settings.channel.replace('_', ' ');

            FontMetrics fm = g.getFontMetrics();
            Font font = g.getFont();
            float s = font.getSize();
            int swidth = fm.stringWidth(channel);
            while ((swidth / (double) width > .5) && (--s > 1)) {
              g.setFont(font.deriveFont(s));
              fm = g.getFontMetrics();
              swidth = fm.stringWidth(channel);
            }
            height = fm.getAscent() + fm.getDescent();
            int lw = swidth + 20;
            g.setColor(new Color(255, 255, 255, 192));
            g.fillRect(helicorderViewPanel.getX() + width / 2 - lw / 2, 3, lw, height);
            g.setColor(Color.black);
            g.drawRect(helicorderViewPanel.getX() + width / 2 - lw / 2, 3, lw, height);

            g.drawString(channel, helicorderViewPanel.getX() + width / 2 - swidth / 2,
                height - fm.getDescent());
            g.setFont(oldFont);
          }
          try {
            final PngEncoderB png = new PngEncoderB(image, false, PngEncoder.FILTER_NONE, 7);
            final FileOutputStream out = new FileOutputStream(f);
            final byte[] bytes = png.pngEncode();
            out.write(bytes);
            out.close();
          } catch (final Exception ex) {
            ex.printStackTrace();
          }
        } else {    // if tag mode not enabled (image is scaled to display nicely)
          int width = -1;
          int height = -1;
          try {
            width = Integer.parseInt(widthTextField.getText());
            height = Integer.parseInt(heightTextField.getText());
          } catch (final Exception ex) {
            ex.printStackTrace();
          }
          if (width <= 0 || height <= 0) {
            JOptionPane.showMessageDialog(HelicorderViewerFrame.this, "Illegal width or height.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
          }
  
          final Plot plot = new Plot(width, height);
  
          Double end = settings.getBottomTime();
          if (Double.isNaN(end)) {
            end = J2kSec.now();
          }
  
          final Double before = end - settings.span * 60;
          final int tc = 30;
  
          // TODO: this should use existing data.
          final HelicorderData heliData =
              dataSource.getHelicorder(settings.channel, before - tc, end + tc, null);
          final HelicorderRenderer heliRenderer =
              new HelicorderRenderer(heliData, settings.timeChunk);
          if (swarmConfig.heliColors != null) {
            heliRenderer.setDefaultColors(swarmConfig.heliColors); // DCK: add configured colors
          }
  
          heliRenderer.setChannel(settings.channel);
          heliRenderer.setLocation(HelicorderViewPanel.X_OFFSET, HelicorderViewPanel.Y_OFFSET,
              width - HelicorderViewPanel.X_OFFSET - HelicorderViewPanel.RIGHT_WIDTH,
              height - HelicorderViewPanel.Y_OFFSET - HelicorderViewPanel.BOTTOM_HEIGHT);
          heliRenderer.setHelicorderExtents(before, end, -1 * Math.abs(settings.barRange),
              Math.abs(settings.barRange));
          heliRenderer.setTimeZone(swarmConfig.getTimeZone(settings.channel));
          heliRenderer.setForceCenter(settings.forceCenter);
          heliRenderer.setClipBars(settings.clipBars);
          heliRenderer.setShowClip(settings.showClip);
          heliRenderer.setClipValue(settings.clipValue);
          heliRenderer.setChannel(settings.channel);
          heliRenderer.setLargeChannelDisplay(includeChannel.isSelected());
          heliRenderer.createDefaultAxis();
          plot.addRenderer(heliRenderer);
  
          if (fileFormatCb.getSelectedItem().equals("PS")) {
            plot.writePS(f.getAbsolutePath());
          } else {
            try {
              plot.writePNG(f.getAbsolutePath());
            } catch (final PlotException e1) {
              e1.printStackTrace();
            }
          }
        }

        swarmConfig.lastPath = f.getParent();
      }

      chooser.setAccessory(null);
    }
  }
  
  public boolean isTagEnabled() {
    return tagButton.isSelected();
  }

  public void disableTag() {
    tagButton.setSelected(false);
  }
  
  //Add Megans code
  
  public void acceptGroundTruth(File fs) throws IOException {
    

//  File myFile = new File(“20190305__Run Logs-20190305.xlsx”);
    FileInputStream file = new FileInputStream(fs);
    ArrayList<Pair<Date, Date>> dates = new ArrayList<Pair<Date, Date>>();
  
    XSSFWorkbook wb = new XSSFWorkbook(file);
    XSSFSheet sheet = wb.getSheetAt(0);

    int underIndex = 0;
    String filename = groundTruthFile.getName();
    int fileLength = filename.length();
    for (int i = 0; i < fileLength; i++)
    {
      if (filename.charAt(i) == '_' )
      {
        underIndex = i;
      }
    }
    dateString = filename.substring(0, underIndex - 1);
    System.out.println("date string " + dateString);
    
    int year = Integer.parseInt(dateString.substring(0, 4));
//    int year = 1989;
    int month = Integer.parseInt(dateString.substring(4, 6)) - 1;
    int day = Integer.parseInt(dateString.substring(6));
    
    System.out.println("year " + year);
    System.out.println("month " + month);
    System.out.println("day " + day);
  
    Iterator<Row> rowIterator = sheet.iterator();
    int rowCount = 0;
    int columnCount = 0;
    int startColumn = 0;
    int endColumn = 0;
    while (rowIterator.hasNext())
    {
        columnCount = 0;
        Row myRow = rowIterator.next();
        Iterator<Cell> cellIterator = myRow.cellIterator();
  
        String startTime = "";
        String endTime = "";
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        cal.set(year, month, day);
        Date javaStartDate = new Date();
        Date javaEndDate = new Date();
  
        while (cellIterator.hasNext())
        {
            Cell cell = cellIterator.next();
            if (cell.toString().contains("Start"))
            {
              startColumn = columnCount;
            }
            else if (cell.toString().contains("End"))
            {
              endColumn = columnCount;
            }
            
            switch (cell.getCellType())
            {
            case NUMERIC:
                if (rowCount > 1 && columnCount == startColumn) // start time
                {
                    Date javaStart = DateUtil.getJavaDate((double)cell.getNumericCellValue());
                    startTime = new SimpleDateFormat("HH:mm:ss").format(javaStart);

                    int hour = Integer.parseInt(startTime.substring(0, 2));
                    int min = Integer.parseInt(startTime.substring(3, 5));
                    int sec = Integer.parseInt(startTime.substring(6));
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, min);
                    cal.set(Calendar.SECOND, sec);

                    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                    javaStartDate = cal.getTime();

                }
                else if (rowCount > 1 && columnCount == endColumn) // end time
                {
                  Date javaEnd = DateUtil.getJavaDate((double)cell.getNumericCellValue());
                  SimpleDateFormat end = new SimpleDateFormat("HH:mm:ss");
                  endTime = new SimpleDateFormat("HH:mm:ss").format(javaEnd);

                  int hour = Integer.parseInt(endTime.substring(0, 2));
                  int min = Integer.parseInt(endTime.substring(3, 5));
                  int sec = Integer.parseInt(endTime.substring(6));
                  cal.set(Calendar.HOUR_OF_DAY, hour);
                  cal.set(Calendar.MINUTE, min);
                  cal.set(Calendar.SECOND, sec);

                  TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                  javaEndDate = cal.getTime();
  
                }
                break;
            }
            columnCount++;
        }
        rowCount++;
  
        if (endTime != "")
        {

          dates.add(new Pair<Date, Date>(javaStartDate, javaEndDate));

        }
  
    }
    HelicorderGroundTruthDialog d = new HelicorderGroundTruthDialog(this, dates);
//    if (firstGroundOpening != true)
//    {
//      d.setVisible(true);
//    }
//    firstGroundOpening = false;
    d.setVisible(true);
    file.close();
    
  
  }

  public void createCustomWaveInset(Date javaStartDate, Date javaEndDate)
  {
    int underIndex = 0;
    String filename = groundTruthFile.getName();
    int fileLength = filename.length();
    for (int i = 0; i < fileLength; i++)
    {
      if (filename.charAt(i) == '_' )
      {
        underIndex = i;
      }
    }
    String dateString = filename.substring(0, underIndex - 1);

    int year = 1989;
    int month = Integer.parseInt(dateString.substring(4, 6)) - 1;
    int day = Integer.parseInt(dateString.substring(6));
    
    System.out.println("creating GT month " + month);
    System.out.println("creating GT day " + day);
    
    SimpleDateFormat star = new SimpleDateFormat("HH:mm:ss");
    star.setTimeZone(TimeZone.getTimeZone("UTC"));
    String startTime = new SimpleDateFormat("HH:mm:ss").format(javaStartDate);
    String swarmStartTime = star.format(javaStartDate);
    
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    cal.set(year, month, day);
    
    int hour = Integer.parseInt(swarmStartTime.substring(0, 2));
    int min = Integer.parseInt(swarmStartTime.substring(3, 5));
    int sec = Integer.parseInt(swarmStartTime.substring(6));

    cal.set(Calendar.HOUR_OF_DAY, hour);
    cal.set(Calendar.MINUTE, min);
    cal.set(Calendar.SECOND, sec);
    cal.add(Calendar.HOUR_OF_DAY, -12);
    
    javaStartDate = cal.getTime();

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    cal.set(year, month, day);
    
    SimpleDateFormat end = new SimpleDateFormat("HH:mm:ss");
    end.setTimeZone(TimeZone.getTimeZone("UTC"));
    String endTime = new SimpleDateFormat("HH:mm:ss").format(javaEndDate);
    String swarmEndTime = end.format(javaEndDate);

    hour = Integer.parseInt(swarmEndTime.substring(0, 2));
    min = Integer.parseInt(swarmEndTime.substring(3, 5));
    sec = Integer.parseInt(swarmEndTime.substring(6));

    cal.set(Calendar.HOUR_OF_DAY, hour);
    cal.set(Calendar.MINUTE, min);
    cal.set(Calendar.SECOND, sec);
    cal.add(Calendar.HOUR_OF_DAY, -12);
    
    javaEndDate = cal.getTime();

    float difference = javaEndDate.getTime() - javaStartDate.getTime();
    difference = difference / 1000;
  
    long middle = javaEndDate.getTime() + javaStartDate.getTime();
    middle = middle / 2;
    Date middleDate = new Date(middle);
  
    double startEw = Ew.fromDate(javaStartDate);
    double endEw = Ew.fromDate(javaEndDate);
    double middleEw = Ew.fromDate(middleDate);

    getHelicorder();
    settings.waveZoomOffset = (int)difference / 2;
    helicorderViewPanel.settingsChanged();
    helicorderViewPanel.createWaveInset(middleEw, 307, 250);
    helicorderViewPanel.setStartMark(startEw);
    helicorderViewPanel.setEndMark(endEw);
  }
  
  
  //end megans code
}
