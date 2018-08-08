package rn.react_native_camera.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.blankj.utilcode.utils.CloseUtils;
import com.blankj.utilcode.utils.ImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class ImageCompressor {
    public static final int FIRST_GEAR = 1;
    public static final int THIRD_GEAR = 3;

    private static final String TAG = "ImageCompressor";
    private final static String DEFAULT_DISK_CACHE_DIR = "compress_cache";

    private static volatile ImageCompressor INSTANCE;

    private final File mCacheDir;

    private OnCompressListener compressListener;
    private File mFile;
    private int gear = THIRD_GEAR;
    private String filename;

    private ImageCompressor(File cacheDir) {
        mCacheDir = cacheDir;
    }

    /**
     * Returns a directory with a default name in the private cache directory of the application to use to store
     * retrieved media and thumbnails.
     *
     * @param context A context.
     * @see #getPhotoCacheDir(Context, String)
     */
    public static synchronized File getPhotoCacheDir(Context context) {
        return getPhotoCacheDir(context, DEFAULT_DISK_CACHE_DIR);
    }

    /**
     * Returns a directory with the given name in the private cache directory of the application to use to store
     * retrieved media and thumbnails.
     *
     * @param context   A context.
     * @param cacheName The name of the subdirectory in which to store the cache.
     * @see #getPhotoCacheDir(Context)
     */
    private static File getPhotoCacheDir(Context context, String cacheName) {
        File tmpPath = new File(Environment.getExternalStorageDirectory(), cacheName);
        LogUtil.e(TAG, "tmpPath:" + tmpPath);
        if (!tmpPath.exists()) {
            tmpPath.mkdirs();
        }
        return tmpPath;
    }

    public static ImageCompressor get(Context context) {
        if (INSTANCE == null)
            INSTANCE = new ImageCompressor(getPhotoCacheDir(context));
        return INSTANCE;
    }

    public ImageCompressor launch() {

        if (compressListener != null) compressListener.onStart();

        if (gear == FIRST_GEAR)
            Observable.just(mFile)
                    .map(new Func1<File, File>() {
                        @Override
                        public File call(File file) {
                            return firstCompress(file);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            if (compressListener != null) compressListener.onError(throwable);
                        }
                    })
                    .onErrorResumeNext(Observable.<File>empty())
                    .filter(new Func1<File, Boolean>() {
                        @Override
                        public Boolean call(File file) {
                            return file != null;
                        }
                    })
                    .subscribe(new Action1<File>() {
                        @Override
                        public void call(File file) {
                            if (compressListener != null) compressListener.onSuccess(file);
                        }
                    });
        else if (gear == THIRD_GEAR)
            Observable.just(mFile)
                    .map(new Func1<File, File>() {
                        @Override
                        public File call(File file) {
                            return thirdCompress(file);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            if (compressListener != null) compressListener.onError(throwable);
                        }
                    })
                    .onErrorResumeNext(Observable.<File>empty())
                    .filter(new Func1<File, Boolean>() {
                        @Override
                        public Boolean call(File file) {
                            return file != null;
                        }
                    })
                    .subscribe(new Action1<File>() {
                        @Override
                        public void call(File file) {
                            if (compressListener != null) compressListener.onSuccess(file);
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    });

        return this;
    }

    public ImageCompressor load(File file) {
        mFile = file;
        return this;
    }

    public ImageCompressor load(String filePath) {
        mFile = new File(filePath);
        return this;
    }

    public ImageCompressor setCompressListener(OnCompressListener listener) {
        compressListener = listener;
        return this;
    }

    public ImageCompressor putGear(int gear) {
        this.gear = gear;
        return this;
    }

    /**
     * @deprecated
     */
    public ImageCompressor setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public Observable<File> asObservable() {
        if (gear == FIRST_GEAR)
            return Observable.just(mFile).map(new Func1<File, File>() {
                @Override
                public File call(File file) {
                    return firstCompress(file);
                }
            });
        else if (gear == THIRD_GEAR)
            return Observable.just(mFile).map(new Func1<File, File>() {
                @Override
                public File call(File file) {
                    return thirdCompress(file);
                }
            });
        else return Observable.empty();
    }

    private File thirdCompress(@NonNull File file) {
        String thumb = mCacheDir.getAbsolutePath() + File.separator + (TextUtils.isEmpty(filename) ? System.currentTimeMillis() : filename) + ".jpg";
        double size;
        String filePath = file.getAbsolutePath();

        int angle = getImageSpinAngle(filePath);
        int width = getImageSize(filePath)[0];
        int height = getImageSize(filePath)[1];
        int thumbW = width % 2 == 1 ? width + 1 : width;
        int thumbH = height % 2 == 1 ? height + 1 : height;

        width = thumbW > thumbH ? thumbH : thumbW;
        height = thumbW > thumbH ? thumbW : thumbH;

        double scale = ((double) width / height);

        if (scale <= 1 && scale > 0.5625) {
            if (height < 1664) {
                if (file.length() / 1024 < 150) return file;

                size = (width * height) / Math.pow(1664, 2) * 150;
                size = size < 60 ? 60 : size;
            } else if (height >= 1664 && height < 4990) {
                thumbW = width / 2;
                thumbH = height / 2;
                size = (thumbW * thumbH) / Math.pow(2495, 2) * 300;
                size = size < 60 ? 60 : size;
            } else if (height >= 4990 && height < 10240) {
                thumbW = width / 4;
                thumbH = height / 4;
                size = (thumbW * thumbH) / Math.pow(2560, 2) * 300;
                size = size < 100 ? 100 : size;
            } else {
                int multiple = height / 1280 == 0 ? 1 : height / 1280;
                thumbW = width / multiple;
                thumbH = height / multiple;
                size = (thumbW * thumbH) / Math.pow(2560, 2) * 300;
                size = size < 100 ? 100 : size;
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            if (height < 1280 && file.length() / 1024 < 200) return file;

            int multiple = height / 1280 == 0 ? 1 : height / 1280;
            thumbW = width / multiple;
            thumbH = height / multiple;
            size = (thumbW * thumbH) / (1440.0 * 2560.0) * 400;
            size = size < 100 ? 100 : size;
        } else {
            int multiple = (int) Math.ceil(height / (1280.0 / scale));
            thumbW = width / multiple;
            thumbH = height / multiple;
            size = ((thumbW * thumbH) / (1280.0 * (1280 / scale))) * 500;
            size = size < 100 ? 100 : size;
        }

        return compress(filePath, thumb, thumbW, thumbH, angle, (long) size);
    }

    private File firstCompress(@NonNull File file) {
        int minSize = 60;
        int longSide = 720;
        int shortSide = 1280;

        String filePath = file.getAbsolutePath();
        String thumbFilePath = mCacheDir.getAbsolutePath() + File.separator +
                (TextUtils.isEmpty(filename) ? System.currentTimeMillis() : filename) + ".jpg";

        long size = 0;
        long maxSize = file.length() / 5;

        int angle = getImageSpinAngle(filePath);
        int[] imgSize = getImageSize(filePath);
        int width = 0, height = 0;
        if (imgSize[0] <= imgSize[1]) {
            double scale = (double) imgSize[0] / (double) imgSize[1];
            if (scale <= 1.0 && scale > 0.5625) {
                width = imgSize[0] > shortSide ? shortSide : imgSize[0];
                height = width * imgSize[1] / imgSize[0];
                size = minSize;
            } else if (scale <= 0.5625) {
                height = imgSize[1] > longSide ? longSide : imgSize[1];
                width = height * imgSize[0] / imgSize[1];
                size = maxSize;
            }
        } else {
            double scale = (double) imgSize[1] / (double) imgSize[0];
            if (scale <= 1.0 && scale > 0.5625) {
                height = imgSize[1] > shortSide ? shortSide : imgSize[1];
                width = height * imgSize[0] / imgSize[1];
                size = minSize;
            } else if (scale <= 0.5625) {
                width = imgSize[0] > longSide ? longSide : imgSize[0];
                height = width * imgSize[1] / imgSize[0];
                size = maxSize;
            }
        }

        return compress(filePath, thumbFilePath, width, height, angle, size);
    }

    /**
     * obtain the image's width and height
     *
     * @param imagePath the path of image
     */
    public int[] getImageSize(String imagePath) {
        int[] res = new int[2];

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;
        BitmapFactory.decodeFile(imagePath, options);

        res[0] = options.outWidth;
        res[1] = options.outHeight;

        return res;
    }

    /**
     * obtain the thumbnail that specify the size
     *
     * @param imagePath the target image path
     * @param width     the width of thumbnail
     * @param height    the height of thumbnail
     * @return {@link Bitmap}
     */
    private Bitmap compress(String imagePath, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);

        int outH = options.outHeight;
        int outW = options.outWidth;
        int inSampleSize = 1;

        if (outH > height || outW > width) {
            int halfH = outH / 2;
            int halfW = outW / 2;

            while ((halfH / inSampleSize) > height && (halfW / inSampleSize) > width) {
                inSampleSize *= 2;
            }
        }

        options.inSampleSize = inSampleSize;

        options.inJustDecodeBounds = false;

        int heightRatio = (int) Math.ceil(options.outHeight / (float) height);
        int widthRatio = (int) Math.ceil(options.outWidth / (float) width);

        if (heightRatio > 1 || widthRatio > 1) {
            if (heightRatio > widthRatio) {
                options.inSampleSize = heightRatio;
            } else {
                options.inSampleSize = widthRatio;
            }
        }
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(imagePath, options);
    }

    /**
     * obtain the image rotation angle
     *
     * @param path path of target image
     */
    private int getImageSpinAngle(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * 指定参数压缩图片
     * create the thumbnail with the true rotate angle
     *
     * @param largeImagePath the big image path
     * @param thumbFilePath  the thumbnail path
     * @param width          width of thumbnail
     * @param height         height of thumbnail
     * @param angle          rotation angle of thumbnail
     * @param size           the file size of image
     */
    private File compress(String largeImagePath, String thumbFilePath, int width, int height, int angle, long size) {
        Bitmap thbBitmap = compress(largeImagePath, width, height);

        thbBitmap = rotatingImage(angle, thbBitmap);

        return saveImage(thumbFilePath, thbBitmap, size);
    }

    /**
     * 旋转图片
     * rotate the image with specified angle
     *
     * @param angle  the angle will be rotating 旋转的角度
     * @param bitmap target image               目标图片
     */
    private static Bitmap rotatingImage(int angle, Bitmap bitmap) {
        //rotate image
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        //create a new image
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * 保存图片到指定路径
     * Save image with specified size
     *
     * @param filePath the image file deleteItem path 储存路径
     * @param bitmap   the image what be deleteItem   目标图片
     * @param size     the file size of image   期望大小
     */
    private File saveImage(String filePath, Bitmap bitmap, long size) {

        File result = new File(filePath.substring(0, filePath.lastIndexOf("/")));

        if (!result.exists() && !result.mkdirs()) return null;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int options = 100;
        bitmap.compress(Bitmap.CompressFormat.JPEG, options, stream);

        while (stream.toByteArray().length / 1024 > size && options > 6) {
            stream.reset();
            options -= 6;
            bitmap.compress(Bitmap.CompressFormat.JPEG, options, stream);
        }

        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            fos.write(stream.toByteArray());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new File(filePath);
    }

    /***
     * 从相册选取照片
     * @param filePath
     * @return
     */
    public static Bitmap loadBitmapFile(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;
        BitmapFactory.decodeFile(filePath, options);
        LogUtil.e(TAG, "src:" + (options.outWidth + "x" + options.outHeight));
        int inSampleSize = calculateInSampleSize(options, 1920);
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        Bitmap src = BitmapFactory.decodeFile(filePath, options);
        float scaledRate = FileUtils.getScaledRate(src, 1920);
        LogUtil.e(TAG, "sampled:scaleRate=" + scaledRate + "," + (src.getWidth() + "x" + src.getHeight()) + "," + (ImageUtils.bitmap2Bytes(src, Bitmap.CompressFormat.JPEG).length / 1024 + "kb"));
        if (scaledRate != 1) {
            src = scaleImage(src, (int) (src.getWidth() * scaledRate), (int) (src.getHeight() * scaledRate));
        }
        LogUtil.e(TAG, "scaled:" + (src.getWidth() + "x" + src.getHeight()) + "," + (ImageUtils.bitmap2Bytes(src, Bitmap.CompressFormat.JPEG).length / 1024 + "kb"));
        return src;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int largerSideMaxLength) {
        int inSampleSize = 1;
        int lagerSide = Math.max(options.outWidth, options.outHeight);//宽高中较大的一边的长度(像素)
        if (lagerSide > largerSideMaxLength) {
            int lagerSideHalf = lagerSide / 2;
            while (lagerSideHalf / inSampleSize > largerSideMaxLength) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static Bitmap scaleImage(Bitmap src, int scaleWidth, int scaleHeight) {
        if (src == null) return null;
        Bitmap res = Bitmap.createScaledBitmap(src, scaleWidth, scaleHeight, false);
        if (res == null) {
            return src;
        }
        if (res != src && !src.isRecycled()) {
            src.recycle();
        }
        return res;
    }

    /**
     * 保存图片
     *
     * @param src      源图片
     * @param filePath 要保存到的文件
     * @param format   格式
     * @param quality  压缩质量
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, String filePath, Bitmap.CompressFormat format, int quality) {
        if (src == null || TextUtils.isEmpty(filePath)) return false;
        System.out.println(src.getWidth() + "," + src.getHeight());
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(filePath));
            return src.compress(format, quality, fos);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            CloseUtils.closeIO(fos);
        }
    }

    public interface OnCompressListener {

        /**
         * Fired when the compression is started, override to handle in your own code
         */
        void onStart();

        /**
         * Fired when a compression returns successfully, override to handle in your own code
         */
        void onSuccess(File file);

        /**
         * Fired when a compression fails to complete, override to handle in your own code
         */
        void onError(Throwable e);
    }
}
