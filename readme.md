this is android h264 streaming player

key feature:

use tcp client;
use MediaCodec for handware decode


server side:
ffmpeg -y -f v4l2 -i /dev/video0 -vcodec libx264 -pix_fmt yuv420p -maxrate 1M -bufsize 4M -f h264 -
