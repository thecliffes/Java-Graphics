
/*
CS-255 Getting started code for the assignment
I do not give you permission to post this code online
Do not post your solution online
Do not copy code
Do not use JavaFX functions or other libraries to do the main parts of the assignment:
	1. Creating a resized image (you must implement nearest neighbour and bilinear interpolation yourself
	2. Gamma correcting the image
	3. Creating the image which has all the thumbnails and event handling to change the larger image
All of those functions must be written by yourself
You may use libraries / IDE to achieve a better GUI
*/
import java.io.*;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Toggle;
//import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class test extends Application {
    short cthead[][][]; //store the 3D volume data set
    float grey[][][]; //store the 3D volume data set converted to 0-1 ready to copy to the image
    short min, max; //min/max value in the 3D volume data set
    int imageSize;
    int slice;
    double gamma=1;
    double[] lookTable = new double[256];
    ImageView TopView;
    Boolean button = true;

    @Override
    public void start(Stage stage) throws FileNotFoundException {
        stage.setTitle("CThead Viewer");

        try {
            ReadData();
        } catch (IOException e) {
            System.out.println("Error: The CThead file is not in the working directory");
            System.out.println("Working Directory = " + System.getProperty("user.dir"));
            return;
        }

        //int width=1024, height=1024; //maximum size of the image
        //We need 3 things to see an image
        //1. We need to create the image
        Image top_image=GetSlice(76); //go get the slice image
        setImageSize(256);
        //2. We create a view of that image
        TopView = new ImageView(top_image); //and then see 3. below
        gammaChange();

        //Create the simple GUI
        final ToggleGroup group = new ToggleGroup();

        RadioButton rb1 = new RadioButton("Nearest neighbour");
        rb1.setToggleGroup(group);
        rb1.setSelected(true);

        RadioButton rb2 = new RadioButton("Bilinear");
        rb2.setToggleGroup(group);

        Slider szslider = new Slider(32, 1024, 256);

        Slider gamma_slider = new Slider(.1, 4, 1);

        //Radio button changes between nearest neighbour and bilinear
        group.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            public void changed(ObservableValue<? extends Toggle> ob, Toggle o, Toggle n) {

                if (rb1.isSelected()) {
                    System.out.println("Radio button 1 clicked");
                    button = true;
                } else if (rb2.isSelected()) {
                    System.out.println("Radio button 2 clicked");
                    button = false;
                }
            }
        });

        //Size of main image changes (slider)
        szslider.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(ObservableValue <? extends Number >
                                        observable, Number oldValue, Number newValue) {

                System.out.println(newValue.intValue());
                //Here's the basic code you need to update an image
                TopView.setImage(null); //clear the old image

                //nearest neighbour
                if(rb1.isSelected()){
                    Image newImage=NearNeigh(newValue.intValue(),slice); //get new image
                    setImageSize(newValue.intValue());
                    TopView.setImage(newImage); //Update the GUI so the new image is displayed
                }
                //bilinear interpolation
                else {
                    Image newImage=Bilinear(newValue.intValue(), slice); //get new image
                    setImageSize(newValue.intValue());
                    TopView.setImage(newImage); //Update the GUI so the new image is displayed
                }
            }
        });

        //Gamma value changes
        gamma_slider.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(ObservableValue <? extends Number >
                                        observable, Number oldValue, Number newValue) {
                gamma = newValue.doubleValue(); //new gamma val
                gammaChange(); //calculates new lookup table
                TopView.setImage(null); //clear image
                //nearest neighbour
                if(rb1.isSelected()){
                    Image newImage=NearNeigh(getImageSize(),slice); //get new image size
                    TopView.setImage(newImage); //Update the GUI so the new image is displayed
                }
                //bilinear interpolation
                else {
                    Image newImage=Bilinear(getImageSize(), slice); //get new image size
                    TopView.setImage(newImage); //Update the GUI so the new image is displayed
                }
            }
        });

        VBox root = new VBox();

        //Add all the GUI elements
        //3. (referring to the 3 things we need to display an image)
        //we need to add it to the layout
        root.getChildren().addAll(rb1, rb2, gamma_slider,szslider, TopView);

        //Display to user
        Scene scene = new Scene(root, 1024, 768);
        stage.setScene(scene);
        stage.show();

        ThumbWindow(scene.getX()+200, scene.getY()+200);
    }

    public void gammaChange(){
        for(int i=0;i<256;i++){ //goes through 256 entries in table
            lookTable[i] = (Math.pow((float)i/255, 1/gamma)); //calculate specific value for lookup table
        }
    }

    //Function to read in the cthead data set
    public void ReadData() throws IOException {
        //File name is hardcoded here - much nicer to have a dialog to select it and capture the size from the user
        File file = new File("CThead");
        //Read the data quickly via a buffer (in C++ you can just do a single fread - I couldn't find the equivalent in Java)
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));

        int i, j, k; //loop through the 3D data set

        min=Short.MAX_VALUE; max=Short.MIN_VALUE; //set to extreme values
        short read; //value read in
        int b1, b2; //data is wrong Endian (check wikipedia) for Java so we need to swap the bytes around

        cthead = new short[113][256][256]; //allocate the memory - note this is fixed for this data set
        grey= new float[113][256][256];
        //loop through the data reading it in
        for (k=0; k<113; k++) {
            for (j=0; j<256; j++) {
                for (i=0; i<256; i++) {
                    //because the Endianess is wrong, it needs to be read byte at a time and swapped
                    b1=((int)in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
                    b2=((int)in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
                    read=(short)((b2<<8) | b1); //and swizzle the bytes around
                    if (read<min) min=read; //update the minimum
                    if (read>max) max=read; //update the maximum
                    cthead[k][j][i]=read; //put the short into memory (in C++ you can replace all this code with one fread)
                }
            }
        }
        System.out.println(min+" "+max); //diagnostic - for CThead this should be -1117, 2248
        //(i.e. there are 3366 levels of grey, and now we will normalise them to 0-1 for display purposes
        //I know the min and max already, so I could have put the normalisation in the above loop, but I put it separate here
        for (k=0; k<113; k++) {
            for (j=0; j<256; j++) {
                for (i=0; i<256; i++) {
                    grey[k][j][i]=((float) cthead[k][j][i]-(float) min)/((float) max-(float) min);
                }
            }
        }

    }

    public Image NearNeigh(int newVal, int slice) {
        WritableImage image = new WritableImage(newVal, newVal);
        int width = (int)image.getWidth();
        int height = (int)image.getHeight();
        int val;
        float value;

        PixelWriter image_writer = image.getPixelWriter();
        for (int y = 0; y < width - 1; y++) {
            for (int x = 0; x < height - 1; x++) {
                float k = y * 256 / (float) newVal; //works out x and y values for original image
                float l = x * 256 / (float) newVal;

                value = grey[slice][(int) k][(int) l]; //finds colour of original image coordinates
                Color color = Color.color(value, value,value);

                val = (int) (color.getRed() * 255);
                Color brightness = Color.color(lookTable[val], lookTable[val], lookTable[val]); //get gamma corrected values for color

                //Apply the new colour
                image_writer.setColor(x, y, brightness); //set pixel in new image location

            }
        }

        return image;
    }

    // v2 (px0,py1) +------+ v3 (px1,py1)
    //              |    X | v, X=(px,py)
    //              |      |
    // v0 (px0,py0) +------+ v1 (px1,py0)
    // v are the values, and px0 and px1 are the x coordinates, and py0 and py1 are
    //the y coordinates
    //        but since only the offset is important, we can assume px0=0, px1=1, py0=0,
    //        py1=1 and X=px,py is the fractional offset from px0,py0

    public Image Bilinear(int newVal, int slice){
        WritableImage image = new WritableImage(newVal, newVal);
        int width = (int)image.getWidth();
        int height = (int)image.getHeight();
        float v0,v1,v2,v3;
        double px0=0,px1=0;
        double py0=0,py1=0;
        double py=0,px=0;
        int val;
        Color brightness=null;

        PixelWriter image_writer = image.getPixelWriter();

        for(double y=0;y<width-1;y++){
            for(double x=0;x<height-1;x++) {
                double k = y * 256 /  (double) newVal;
                double l = x * 256 / (double) newVal;

                py = k; //pixel on y axis
                px = l; //pixel on x axis
                px0 = Math.floor(l); //finds pixels surrounding x and y
                py0 = Math.floor(k);
                py1 = Math.ceil(k);
                px1 = Math.ceil(l);
                if(py1 > 255) { //if pixel exceeds 255 set to 255
                    py1 = 255;
                }
                if(px1 > 255) {
                    px1 = 255;
                }

                v0 = grey [slice][(int)py0][(int)px0]; //find values of 4 pixels surrounding
                v1 = grey [slice][(int)py0][(int)px1];
                v2 = grey [slice][(int)py1][(int)px0];
                v3 = grey [slice][(int)py1][(int)px1];
                float vx = (float) lerp(v2, v3, px0, px1, px); //does bilinear interpolation
                float vy = (float) lerp(v0, v1, px0, px1, px);
                float v = (float) lerp(vy, vx, py0, py1, py);


                Color color1 = Color.color(v, v, v);
                val = (int) (color1.getRed() * 255);
                brightness = Color.color(lookTable[val],lookTable[val],lookTable[val]); //finds value from gamma corrected values

                //Apply the new colour
                image_writer.setColor((int) x, (int) y, brightness);
            }

        }
        return image;
    }


    public double lerp(float v1, float v2, double p1, double p2, double p){
        return v1 + (v2 - v1) * ((p-p1)/(p2-p1)); //linear interpolation method
    }



    //Gets an image from slice 76
    public Image GetSlice(int slice) {
        WritableImage image = new WritableImage(256, 256);
        //Find the width and height of the image to be process
        int width = (int)image.getWidth();
        int height = (int)image.getHeight();
        float val;

        //Get an interface to write to that image memory
        PixelWriter image_writer = image.getPixelWriter();

        //Iterate over all pixels
        for(int y = 0; y < width; y++) {
            for(int x = 0; x < height; x++) {
                //For each pixel, get the colour from the cthead slice 76
                val=grey[slice][y][x];
                Color color=Color.color(val,val,val);

                //Apply the new colour
                image_writer.setColor(x, y, color);
            }
        }
        return image;
    }


    public void ThumbWindow(double atX, double atY) {
        StackPane ThumbLayout = new StackPane();

        WritableImage thumb_image = new WritableImage(720, 720);
        ImageView thumb_view = new ImageView(thumb_image);
        ThumbLayout.getChildren().add(thumb_view);


        {
            Color color = Color.color(1,1,1);
            PixelWriter image_writer = thumb_image.getPixelWriter();
            for(int y = 0; y < thumb_image.getHeight(); y++) {
                for(int x = 0; x < thumb_image.getWidth(); x++) {
                    image_writer.setColor(x,y, color);
                }
            }

            for(int i=0;i<113;i++){
                Image image = NearNeigh(50, i);
                PixelReader read= image.getPixelReader();

                int dx  = (i % 12) * 60; //finds start of thumbnail
                int dy = (i / 12) * 60;

                for(int y=0; y<50;y++){
                    for(int x=0;x<50;x++){
                        color = read.getColor(x,y);
                        image_writer.setColor((dx+x),(dy+y),color); //fills in thumbnail from starting coordinates gathered
                    }
                }
            }
        }

        Scene ThumbScene = new Scene(ThumbLayout, thumb_image.getWidth(), thumb_image.getHeight());

        //Add mouse over handler - the large image is change to the image the mouse is over
        thumb_view.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            slice = slice(event.getX(), event.getY());
            if (button) {
                TopView.setImage(NearNeigh(getImageSize(), slice));
            } else
                TopView.setImage(Bilinear(getImageSize(), slice));
            event.consume();

        });

        //Build and display the new window
        Stage newWindow = new Stage();
        newWindow.setTitle("CThead Slices");
        newWindow.setScene(ThumbScene);

        // Set position of second window, related to primary window.
        newWindow.setX(atX);
        newWindow.setY(atY);

        newWindow.show();
    }

    private int slice(double mx, double my){
        int x = (int) mx; //reverse engineers method to find starting pixels to get the slice youre hovering over
        int y = (int) my;
        int s = (x/60) + (y/60*12);
        if(s > 112){
            s = 112;
        }
        return s;
    }

    public int getImageSize() {
        return imageSize;
    }

    public void setImageSize(int imageSize) {
        this.imageSize = imageSize;
    }

    public static void main(String[] args) {
        launch();
    }

}
