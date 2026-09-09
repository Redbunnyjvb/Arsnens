"""Generate deterministic OpenCV observations for the JVM fusion regression (not a phone recording)."""
import argparse
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--dependencies', required=True)
args = parser.parse_args()
sys.path.insert(0, args.dependencies)
import cv2 as cv
import numpy as np

assert cv.__version__.startswith('4.13.'), cv.__version__
K = np.array([[1000., 0, 640.], [0, 1000., 480.], [0, 0, 1.]])
points = np.array([[3933-23.5, 0, 23.5], [3933+23.5, 0, 23.5],
                   [3933+23.5, 0, -23.5], [3933-23.5, 0, -23.5]], np.float32)
truth_r = np.array([np.pi / 2, 0., 0.])
truth_t = np.array([-3933., 0., 600.])
truth_R = cv.Rodrigues(truth_r)[0]
truth_image = cv.projectPoints(points, truth_r, truth_t, K, None)[0]
random = np.random.default_rng(914)
previous_R = truth_R
frames = []
for i in range(160):
    image = (truth_image + random.normal(0, 0.25, (4, 1, 2))).astype(np.float32)
    count, rotations, translations, _ = cv.solvePnPGeneric(points, image, K, None, flags=cv.SOLVEPNP_IPPE)
    choices = []
    for r, t in zip(rotations, translations):
        R = cv.Rodrigues(r)[0]
        error = float(np.mean(np.linalg.norm(cv.projectPoints(points, r, t, K, None)[0] - image, axis=2)))
        angle = np.degrees(np.arccos(np.clip((np.trace(R.T @ previous_R) - 1) / 2, -1, 1)))
        choices.append((error, angle, r, t, R))
    best = min(choices, key=lambda c: c[0])
    prior = min(choices, key=lambda c: c[1])
    chosen = prior if prior[0] <= best[0] * 4 + 2 else best
    previous_R = chosen[4]
    angle = float(np.degrees(np.arccos(np.clip((np.trace(previous_R.T @ truth_R) - 1) / 2, -1, 1))))
    frames.append({'r': chosen[2].ravel().tolist(), 't': chosen[3].ravel().tolist(),
                   'corners': image.reshape(4, 2).tolist(), 'errorPx': chosen[0], 'angleFromTruthDeg': angle})
output = Path(__file__).resolve().parents[1] / 'app/src/test/resources/pose/small-tag-noise.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'opencv': cv.__version__, 'seed': 914, 'noiseSigmaPx': 0.25,
                              'tagSizeMm': 47, 'tagCenterMm': [3933, 0, 0], 'frames': frames}, separators=(',', ':')), encoding='utf-8')
angles = [f['angleFromTruthDeg'] for f in frames]
print(f'Generated {len(frames)} frames; {sum(a > 1.5 for a in angles)} exceed old 1.5-degree gate; '
      f'angle range {min(angles):.2f}..{max(angles):.2f} deg; max local fit {max(f["errorPx"] for f in frames):.2f}px')
