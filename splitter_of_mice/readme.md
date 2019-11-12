LICENSE<br>
Program: splitter_of_mice.py<br>
Authors: Mikhail Milchenko (animal detection), Jack Muskopf (microPET image i/o)<br>
Description: split microPET mice images into individual animal images<br>

Copyright 2017-2019<br>
Washington University, Mallinckrodt Insitute of Radiology. All rights reserved. <br>
This software may not be reproduced, copied, or distributed without written permission of Washington University. <br>
For more information contact Mikhail Milchenko, PhD<br>

ENVIRONMENT REQUREMENTS<br>
Python 3.6.5 with ipywidgets, numpy, pillow, nibabel, and skimage packages<br>

DESCRIPTION<br>
Mouse image splitting technique. Implemented in Python, reusing the ‘ccdb’ code to read/write images. <br>
The algorithm only requires microPET image (img/img.hdr pair) as input. The output is the same format. It detects the number of mice and outputs split images with suffix appended to the original filename according to the following conventions:<br>
1 mouse: _ctr: single image <br>
2 mice: _l, _r: left and right mice.<br>
3-4 mice: _lt, _lb, _rt, _rb: left top, left bottom, right top, right bottom mouse, as they are seen in the GUI viewer.<br>

USAGE<br>
splitter_of_mice.py [options] file_path out_dir<br>

positional arguments:<br>
  file_path   full path to microPET .img file<br>
  out_dir     output directory<br>

optional arguments:<br>
  -n \<int\>    expected number of animals [auto-detect]<br>
  -t \<float\>  separation threshold between 0..1 [0.9]<br>
  -a          save a copy in Analyze 7.5 format to output directory<br>
  -m \<int\>    maximum margin on axial slice in pixels [20]<br>
  -p \<int\>    minimum number of pixels in detectable region [200]<br>
  
NOTE specify the number of animals (-n option) for additional verification of detected regions. 