/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package MyAdjustApp;

import boofcv.abst.denoise.FactoryImageDenoise;
import boofcv.abst.denoise.WaveletDenoiseFilter;
import boofcv.abst.filter.blur.BlurFilter;
import boofcv.abst.segmentation.ImageSuperpixels;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.alg.filter.blur.GBlurImageOps;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.segmentation.ComputeRegionMeanColor;
import boofcv.alg.segmentation.ImageSegmentationOps;
import boofcv.factory.filter.blur.FactoryBlurFilter;
import boofcv.factory.segmentation.ConfigFh04;
import boofcv.factory.segmentation.FactoryImageSegmentation;
import boofcv.factory.segmentation.FactorySegmentationAlg;
import boofcv.gui.BoofSwingUtil;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.gui.feature.VisualizeRegions;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.ConfigLength;
import boofcv.struct.feature.ColorQueue_F32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import org.apache.commons.io.FilenameUtils;
import org.ddogleg.struct.DogArray_I32;

/**
 *
 * @author Medion
 */
public class PhotoGUI extends javax.swing.JFrame {
    //Get multiple files, adjust and save to desktop
    private BufferedImage[] allImages;
    private File[] allFiles;
    //Get file and display
    private File file;
    private BufferedImage bufferedImage;
    private ImageIcon imageIcon;
    private Image image;
    //Init (blur)
    private Planar<GrayU8> fileConverted;
    private Planar<GrayU8> input;
    //Init (paint)
    private BufferedImage bufferedAlpha;
    private ImageType<Planar<GrayF32>> imageType;
    //Threshold
    private GrayF32 fileConvertedGray;
    private GrayU8 binaryInput;
    //Noise
    private GrayF32 noiseInput;
    private GrayF32 noisy;
    private GrayF32 denoised;
    //Paint
    private ImageSuperpixels paint;
    //Convert image to boofcv
    private ImageBase convImage;
    //Check if at least one option is selected before image is saved to desktop
    private boolean checkOptions = false;
    //Size of the blur kernel. Square region with a width of radius*2 + 1
    private final int radius = 8;

    /**
     * Creates new form PhotoGUI
     */
    public PhotoGUI() {
        //Initialise GUI
        initComponents();
        //GUI in center of screen
        this.setLocationRelativeTo(null);
    }
    
    public void displayImageBefore() {
        //Display adjusted image on JLabel
        imageIcon = new ImageIcon(bufferedImage);
        
        //Fit image to JLabel
        image = imageIcon.getImage().getScaledInstance(jLabelImageBefore.getWidth(), jLabelImageBefore.getHeight(), Image.SCALE_SMOOTH);
        
        //Put image on jLabel
        jLabelImageBefore.setIcon(new ImageIcon(image));
        
        //Reset 'Preview'
        jLabelImageAfter.setIcon( null );
    }
    
    public void displayImageAfter() {
        //Display adjusted image on JLabel
        imageIcon = new ImageIcon(bufferedImage);
        
        //Fit image to JLabel
        image = imageIcon.getImage().getScaledInstance(jLabelImageAfter.getWidth(), jLabelImageAfter.getHeight(), Image.SCALE_SMOOTH);
        
        //Put image on jLabel
        jLabelImageAfter.setIcon(new ImageIcon(image));
    }
    
    public void displayImageAfterReset() {
        //Disable 'Preview'-image
        jLabelImageAfter.setIcon( null );
        //Set text of 'Preview'-image
        jLabelImageAfter.setText("Preview");
    }
    
    public void displayImageAfterBlurAdjust() {
        //Convert from BufferedImage to display
        bufferedImage = ConvertBufferedImage.convertTo(input, null, true);
        
        //Display to GUI
        displayImageAfter();
    }
    
    public void displayImageAfterThresholdAdjust() {
        //Convert from Binary to display
        bufferedImage = VisualizeBinaryData.renderBinary(binaryInput, false, null);

        //Display to GUI
        displayImageAfter();
    }
    
    public void displayImageAfterNoisyAdjust() {
        //Convert from BufferedImage to display
        bufferedImage = ConvertBufferedImage.convertTo(noisy, null);

        //Display to GUI
        displayImageAfter();
    }

    public void displayImageAfterDenoiseAdjust() {
        //Convert from BufferedImage to display
        bufferedImage = ConvertBufferedImage.convertTo(denoised, null);

        //Display to GUI
        displayImageAfter();
    }
    
    public void displayImageAfterPaintAdjust() {
        //Convert image into BoofCV format, so we can segment the different pixels of the image
        convImage = imageType.createImage(bufferedAlpha.getWidth(), bufferedAlpha.getHeight());
        ConvertBufferedImage.convertFrom(bufferedAlpha, convImage, true);

        //Segmentation can be done easier by blurring the image first
        GBlurImageOps.gaussian(convImage, convImage, 0.2, radius, null);

        //Segment the different pixels of the image to group pixels
        var imageSegmentation = new GrayS32(convImage.width, convImage.height);
        paint.segment(convImage, imageSegmentation);

        //Get type of image from converted image (BoofCV format) and blur each 'segmentregion' with the mean-blur filter
        ImageType type = convImage.getImageType();
        ComputeRegionMeanColor colorize = FactorySegmentationAlg.regionMeanColor(type);

        //Count all regions created and group them together
        var colorSegmentation = new ColorQueue_F32(type.getNumBands());
        colorSegmentation.resize(paint.getTotalSuperpixels());
        var countDifferentRegions = new DogArray_I32();
        countDifferentRegions.resize(paint.getTotalSuperpixels());
        
        //Finally, put the image back together
        ImageSegmentationOps.countRegionPixels(imageSegmentation, paint.getTotalSuperpixels(), countDifferentRegions.data);
        colorize.process(convImage, imageSegmentation, countDifferentRegions, colorSegmentation);

        //Convert from BufferedImage to display
        bufferedImage = VisualizeRegions.regionsColor(imageSegmentation, colorSegmentation, null);
        
        //Display to GUI
        displayImageAfter();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        jLabelImageBefore = new javax.swing.JLabel();
        jButtonBrowseImage = new javax.swing.JButton();
        jButtonSaveImage = new javax.swing.JButton();
        jRadioButtonGaussian = new javax.swing.JRadioButton();
        jLabelBlurOptions = new javax.swing.JLabel();
        jRadioButtonMedian = new javax.swing.JRadioButton();
        jRadioButtonMean = new javax.swing.JRadioButton();
        jCheckBoxDenoise = new javax.swing.JCheckBox();
        jLabelThresholdOptions = new javax.swing.JLabel();
        jComboBoxThreshold = new javax.swing.JComboBox<>();
        jTextFieldSaveName = new javax.swing.JTextField();
        jLabelName = new javax.swing.JLabel();
        jButtonReset = new javax.swing.JButton();
        jCheckBoxNoisy = new javax.swing.JCheckBox();
        jLabelThresholdOptions1 = new javax.swing.JLabel();
        jLabelImageAfter = new javax.swing.JLabel();
        jButtonBrowseFolder = new javax.swing.JButton();
        jLabelThresholdOptions2 = new javax.swing.JLabel();
        jRadioButtonPaint1 = new javax.swing.JRadioButton();
        jRadioButtonPaint2 = new javax.swing.JRadioButton();
        jRadioButtonPaint3 = new javax.swing.JRadioButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabelImageBefore.setText("Original");

        jButtonBrowseImage.setText("Browse Image");
        jButtonBrowseImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonBrowseImageActionPerformed(evt);
            }
        });

        jButtonSaveImage.setText("Save Image");
        jButtonSaveImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSaveImageActionPerformed(evt);
            }
        });

        buttonGroup1.add(jRadioButtonGaussian);
        jRadioButtonGaussian.setText("Gaussian");
        jRadioButtonGaussian.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jRadioButtonGaussianMouseClicked(evt);
            }
        });

        jLabelBlurOptions.setText("Blur Options:");

        buttonGroup1.add(jRadioButtonMedian);
        jRadioButtonMedian.setText("Median");
        jRadioButtonMedian.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jRadioButtonMedianMouseClicked(evt);
            }
        });

        buttonGroup1.add(jRadioButtonMean);
        jRadioButtonMean.setText("Mean");
        jRadioButtonMean.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jRadioButtonMeanMouseClicked(evt);
            }
        });

        buttonGroup2.add(jCheckBoxDenoise);
        jCheckBoxDenoise.setText("Denoise");
        jCheckBoxDenoise.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jCheckBoxDenoiseMouseClicked(evt);
            }
        });

        jLabelThresholdOptions.setText("Threshold Options:");

        jComboBoxThreshold.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Gaussian", "Mean", "Niblack", "Wolf", "Min-Max" }));
        jComboBoxThreshold.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxThresholdActionPerformed(evt);
            }
        });

        jLabelName.setText("Filename:");

        jButtonReset.setText("Reset");
        jButtonReset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButtonResetMouseClicked(evt);
            }
        });

        buttonGroup2.add(jCheckBoxNoisy);
        jCheckBoxNoisy.setText("Noisy");
        jCheckBoxNoisy.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jCheckBoxNoisyMouseClicked(evt);
            }
        });

        jLabelThresholdOptions1.setText("Noise Options:");

        jLabelImageAfter.setText("Preview");

        jButtonBrowseFolder.setText("Browse Folder");
        jButtonBrowseFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonBrowseFolderActionPerformed(evt);
            }
        });

        jLabelThresholdOptions2.setText("Paint Options:");

        buttonGroup3.add(jRadioButtonPaint1);
        jRadioButtonPaint1.setText("Paint1");
        jRadioButtonPaint1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jRadioButtonPaint1MouseClicked(evt);
            }
        });

        buttonGroup3.add(jRadioButtonPaint2);
        jRadioButtonPaint2.setText("Paint2");
        jRadioButtonPaint2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jRadioButtonPaint2MouseClicked(evt);
            }
        });

        buttonGroup3.add(jRadioButtonPaint3);
        jRadioButtonPaint3.setText("Paint3");
        jRadioButtonPaint3.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jRadioButtonPaint3MouseClicked(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jRadioButtonGaussian, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jRadioButtonMean, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBoxThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabelBlurOptions, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabelThresholdOptions, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jRadioButtonMedian, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(30, 30, 30)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelThresholdOptions1, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jCheckBoxNoisy, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jCheckBoxDenoise, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelThresholdOptions2, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jRadioButtonPaint2, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jRadioButtonPaint1, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jRadioButtonPaint3, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelName, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(14, 14, 14)
                                .addComponent(jTextFieldSaveName, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(8, 8, 8)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jButtonBrowseFolder)
                                    .addComponent(jButtonBrowseImage))))
                        .addGap(18, 18, 18)
                        .addComponent(jLabelImageBefore, javax.swing.GroupLayout.PREFERRED_SIZE, 299, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelImageAfter, javax.swing.GroupLayout.PREFERRED_SIZE, 299, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jButtonReset)
                        .addGap(26, 26, 26)
                        .addComponent(jButtonSaveImage, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(20, 20, 20))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(jButtonBrowseImage)
                        .addGap(11, 11, 11)
                        .addComponent(jButtonBrowseFolder)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabelName)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextFieldSaveName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabelImageBefore, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelImageAfter, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabelThresholdOptions)
                        .addComponent(jLabelBlurOptions))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabelThresholdOptions1)
                        .addComponent(jLabelThresholdOptions2)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(0, 6, Short.MAX_VALUE)
                                .addComponent(jCheckBoxNoisy)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(18, 18, 18)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                            .addComponent(jButtonSaveImage)
                                            .addComponent(jButtonReset)))
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(10, 10, 10)
                                        .addComponent(jCheckBoxDenoise)))
                                .addGap(21, 21, 21))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jRadioButtonGaussian)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jComboBoxThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jRadioButtonMean))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jRadioButtonMedian)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jRadioButtonPaint1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jRadioButtonPaint2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jRadioButtonPaint3)
                        .addGap(0, 0, Short.MAX_VALUE))))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButtonBrowseImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonBrowseImageActionPerformed
        //Get image with FileChooser
        file = BoofSwingUtil.fileChooser(null, null, true, ".", null, BoofSwingUtil.FileTypes.IMAGES);
        //Buffer image to get all data of the image
        bufferedImage = UtilImageIO.loadImageNotNull(file.getAbsolutePath());

        //-----Init(blur)-----
        //Converts a buffered image into an image of the specified type (GrayU8.class)
        fileConverted = ConvertBufferedImage.convertFrom(bufferedImage, true, ImageType.pl(3, GrayU8.class));
        //Creates a new image of the same type which also has the same shape
        input = fileConverted.createSameShape();
        
        //-----Init(paint)-----
        //Returns an image which doesn't have an alpha channel (a special channel that handles transparency)
        bufferedAlpha = ConvertBufferedImage.stripAlphaChannel(bufferedImage);
        //Set type of image
        imageType = ImageType.pl(3, GrayF32.class);
        
        //Display image in GUI
        displayImageBefore();
    }//GEN-LAST:event_jButtonBrowseImageActionPerformed

    private void jButtonSaveImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSaveImageActionPerformed
        try {
            //If the image, selected with FileChooser, is not null
            if (file != null) {
                //Get input text from 'Filename'-textfield
                String outputName = jTextFieldSaveName.getText();
                //Get the extension from the chosen image
                String outputExtension = FilenameUtils.getExtension(file.getAbsolutePath().toLowerCase());
                //If at least one option is selected
                if (checkOptions == true) {
                    //If the 'Filename'-textfield is not empty
                    if (!outputName.isEmpty()) {
                        //Write image to dekstop
                        ImageIO.write(bufferedImage, outputExtension, new File("C:/Dev/Erasmus CODE/Java Advanced/NetBeansProjects/MyAdjustApp/app/src/main/java/MyAdjustApp/images/" + outputName + "." + outputExtension));                     
                        JOptionPane.showMessageDialog(rootPane, "Image saved!");
                        
                        //Open and show on desktop
                        Desktop desktop = Desktop.getDesktop();
                        desktop.open(new File("C:/Dev/Erasmus CODE/Java Advanced/NetBeansProjects/MyAdjustApp/app/src/main/java/MyAdjustApp/images/" + outputName + "." + outputExtension));
                    } else {
                        JOptionPane.showMessageDialog(rootPane, "Give a name to the file in the inputfield");
                    }
                } else {
                    JOptionPane.showMessageDialog(rootPane, "You need to use at least one of the options");
                }
            } else {
                JOptionPane.showMessageDialog(rootPane, "You need to select an image first");
            }
        } catch (IOException ex) {
            Logger.getLogger(PhotoGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_jButtonSaveImageActionPerformed

    private void jRadioButtonGaussianMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jRadioButtonGaussianMouseClicked
        checkOptions = true;
        //Apply gaussian-blur filter
        GBlurImageOps.gaussian(fileConverted, input, -1, radius, null);

        //Display image in GUI after blur filter
        displayImageAfterBlurAdjust();
    }//GEN-LAST:event_jRadioButtonGaussianMouseClicked

    private void jRadioButtonMedianMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jRadioButtonMedianMouseClicked
        checkOptions = true;        
        //Apply median-blur
        BlurImageOps.median(fileConverted, input, radius, radius, null);

        displayImageAfterBlurAdjust();
    }//GEN-LAST:event_jRadioButtonMedianMouseClicked

    private void jRadioButtonMeanMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jRadioButtonMeanMouseClicked
        checkOptions = true;
        //Apply mean-blur
        BlurFilter<Planar<GrayU8>> filterMean = FactoryBlurFilter.mean(fileConverted.getImageType(), radius);
        filterMean.process(fileConverted, input);

        displayImageAfterBlurAdjust();
    }//GEN-LAST:event_jRadioButtonMeanMouseClicked

    private void jButtonResetMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButtonResetMouseClicked
        checkOptions = false;
        //Unselect all
        buttonGroup1.clearSelection();
        buttonGroup2.clearSelection();
        buttonGroup3.clearSelection();
        jComboBoxThreshold.setSelectedIndex(0);

        displayImageAfterReset();
    }//GEN-LAST:event_jButtonResetMouseClicked

    private void jComboBoxThresholdActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxThresholdActionPerformed
        checkOptions = true;
        //Get choice in checkbox
        String choice = jComboBoxThreshold.getSelectedItem().toString();

        //Convert into a usable format
        fileConvertedGray = ConvertBufferedImage.convertFromSingle(bufferedImage, null, GrayF32.class);
        binaryInput = new GrayU8(fileConvertedGray.width, fileConvertedGray.height);

        switch (choice) {
            case "Gaussian":
                GThresholdImageOps.localGaussian(fileConvertedGray, binaryInput, ConfigLength.fixed(85), 1.0, true, null, null);
                displayImageAfterThresholdAdjust();
                break;
            case "Mean":
                GThresholdImageOps.threshold(fileConvertedGray, binaryInput, ImageStatistics.mean(fileConvertedGray), true);
                displayImageAfterThresholdAdjust();
                break;
            case "Niblack":
                GThresholdImageOps.localNiblack(fileConvertedGray, binaryInput, ConfigLength.fixed(11), 0.30f, true);
                displayImageAfterThresholdAdjust();
                break;
            case "Wolf":
                GThresholdImageOps.localWolf(fileConvertedGray, binaryInput, ConfigLength.fixed(11), 0.30f, true);
                displayImageAfterThresholdAdjust();
                break;
            case "Min-Max":
                GThresholdImageOps.blockMinMax(fileConvertedGray, binaryInput, ConfigLength.fixed(21), 1.0, true, 15);
                displayImageAfterThresholdAdjust();
                break;
            default:
                JOptionPane.showMessageDialog(rootPane, "Something went wrong with thresholding");
                break;
        }
    }//GEN-LAST:event_jComboBoxThresholdActionPerformed

    private void jCheckBoxNoisyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jCheckBoxNoisyMouseClicked
        checkOptions = true;
        //Create random number
        Random rand = new Random(234);
        //Load the image
        noiseInput = UtilImageIO.loadImage(file.getAbsolutePath(), GrayF32.class);

        //Create a noisy image
        noisy = noiseInput.clone();
        GImageMiscOps.addGaussian(noisy, rand, 20, 0, 255);
        
        displayImageAfterNoisyAdjust();
    }//GEN-LAST:event_jCheckBoxNoisyMouseClicked

    private void jCheckBoxDenoiseMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jCheckBoxDenoiseMouseClicked
        checkOptions = true;
        //Load the image
        noiseInput = UtilImageIO.loadImage(file.getAbsolutePath(), GrayF32.class);

        //Clone the noisy image with the same shape
        noisy = noiseInput.clone();
        denoised = noisy.createSameShape();
        
        //Ready up noise removal algorithm
        WaveletDenoiseFilter<GrayF32> denoiser = FactoryImageDenoise.waveletBayes(GrayF32.class, 4, 0, 255);
        //Remove noise from image
        denoiser.process(noisy, denoised);
        
        displayImageAfterDenoiseAdjust();
    }//GEN-LAST:event_jCheckBoxDenoiseMouseClicked

    private void jRadioButtonPaint1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jRadioButtonPaint1MouseClicked
        //Apply meanshift filter
        paint = FactoryImageSegmentation.meanShift(null, imageType);
        displayImageAfterPaintAdjust();
    }//GEN-LAST:event_jRadioButtonPaint1MouseClicked

    private void jRadioButtonPaint2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jRadioButtonPaint2MouseClicked
        //Apply fh04 filter
        paint = FactoryImageSegmentation.fh04(new ConfigFh04(100, 30), imageType);
        displayImageAfterPaintAdjust();
    }//GEN-LAST:event_jRadioButtonPaint2MouseClicked

    private void jRadioButtonPaint3MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jRadioButtonPaint3MouseClicked
        //Apply watershed filter
        paint = FactoryImageSegmentation.watershed(null, imageType);
        displayImageAfterPaintAdjust();
    }//GEN-LAST:event_jRadioButtonPaint3MouseClicked

    private void jButtonBrowseFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonBrowseFolderActionPerformed
        JOptionPane.showMessageDialog(rootPane, "With this automatication option, you are able to select a folder and denoise/blur all the noisy images within the folder.");
        
        //Reset 'Original'
        jLabelImageBefore.setIcon( null );
        //Reset 'Preview'
        jLabelImageAfter.setIcon( null );
        
        //Get folder with FileChooser
        file = BoofSwingUtil.fileChooser(null, null, true, ".", null, BoofSwingUtil.FileTypes.DIRECTORIES);
        //System.out.println(file);
        
        //Get folder path
        String folderPath = file.getAbsolutePath();
        
        //Put folder in File object
        File path = new File(folderPath);
        
        //Get all files within folder
        allFiles = path.listFiles();
        
        //Buffer all images
        allImages = new BufferedImage[allFiles.length];
        
        //Set jLabel text to ...
        jLabelImageBefore.setText(allFiles.length + " images in folder are being denoised.");
        
        //Loop through all files
        for(int i = 0; i < allFiles.length; i++){
            try {
                //Read all file paths and put it into an array of buffered images
                allImages[i] = ImageIO.read(allFiles[i]);
                //System.out.println(allImages[i]);
                
                //-----Init(blur)-----
                //Converts a buffered image into an image of the specified type (GrayU8.class)
                fileConverted = ConvertBufferedImage.convertFrom(allImages[i], true, ImageType.pl(3, GrayU8.class));
                //Creates a new image of the same type which also has the same shape
                input = fileConverted.createSameShape();
                //System.out.println(input);
                
                //Apply gaussian-blur
                GBlurImageOps.gaussian(fileConverted, input, -1, radius, null);
               
                //Convert from BufferedImage to display
                allImages[i] = ConvertBufferedImage.convertTo(input, null, true);
                
                try {
                    //If the image, selected with FileChooser, is not null
                    if (allImages[i] != null) {
                        //Get input text from 'Filename'-textfield
                        Long outputName = System.currentTimeMillis();
                        //Get the extension from the chosen image
                        String outputExtension = FilenameUtils.getExtension(allFiles[i].getAbsolutePath().toLowerCase());
                        //If the 'Filename'-textfield is not empty
                        if (outputName != 0) {
                            //Write image to dekstop
                            ImageIO.write(allImages[i], outputExtension, new File("C:/Dev/Erasmus CODE/Java Advanced/NetBeansProjects/MyAdjustApp/app/src/main/java/MyAdjustApp/images/multiple/" + outputName + "." + outputExtension));

                            //Show on desktop
                            Desktop desktop = Desktop.getDesktop();
                            //Open file explorer
                            Desktop.getDesktop().open(new File("C:/Dev/Erasmus CODE/Java Advanced/NetBeansProjects/MyAdjustApp/app/src/main/java/MyAdjustApp/images/multiple"));
                            //Open single image
                            //desktop.open(new File("C:\Dev\Erasmus CODE\Java Advanced\NetBeansProjects\MyAdjustApp\app\src\main\java\MyAdjustApp\images\multiple\" + outputName + (i+1) + "." + outputExtension));
                        } else {
                            JOptionPane.showMessageDialog(rootPane, "Give a name to the file in the inputfield");
                        }
                    } else {
                        JOptionPane.showMessageDialog(rootPane, "You need to select an image first");
                    }
                } catch (IOException ex) {
                    Logger.getLogger(PhotoGUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(PhotoGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //Set jLabel text to ...
        jLabelImageAfter.setText(allFiles.length + " images in folder are successfully denoised!");
        //JOptionPane.showMessageDialog(rootPane, "Images saved!");
    }//GEN-LAST:event_jButtonBrowseFolderActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(PhotoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(PhotoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(PhotoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(PhotoGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new PhotoGUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.JButton jButtonBrowseFolder;
    private javax.swing.JButton jButtonBrowseImage;
    private javax.swing.JButton jButtonReset;
    private javax.swing.JButton jButtonSaveImage;
    private javax.swing.JCheckBox jCheckBoxDenoise;
    private javax.swing.JCheckBox jCheckBoxNoisy;
    private javax.swing.JComboBox<String> jComboBoxThreshold;
    private javax.swing.JLabel jLabelBlurOptions;
    private javax.swing.JLabel jLabelImageAfter;
    private javax.swing.JLabel jLabelImageBefore;
    private javax.swing.JLabel jLabelName;
    private javax.swing.JLabel jLabelThresholdOptions;
    private javax.swing.JLabel jLabelThresholdOptions1;
    private javax.swing.JLabel jLabelThresholdOptions2;
    private javax.swing.JRadioButton jRadioButtonGaussian;
    private javax.swing.JRadioButton jRadioButtonMean;
    private javax.swing.JRadioButton jRadioButtonMedian;
    private javax.swing.JRadioButton jRadioButtonPaint1;
    private javax.swing.JRadioButton jRadioButtonPaint2;
    private javax.swing.JRadioButton jRadioButtonPaint3;
    private javax.swing.JTextField jTextFieldSaveName;
    // End of variables declaration//GEN-END:variables
}
