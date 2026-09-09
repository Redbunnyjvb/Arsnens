"""Independent OpenCV 4.13 geometry checks; complements, does not execute, the Kotlin pipeline.

python scripts/verify_pose_geometry.py --dependencies PATH_TO_TEMP_OPENCV
The optional --official-prints check reads AprilRobotics' small reference PNGs in memory.
"""
import argparse
import itertools
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--dependencies')
parser.add_argument('--official-prints', action='store_true')
args = parser.parse_args()
if args.dependencies:
    sys.path.insert(0, args.dependencies)
import cv2 as cv
import numpy as np

assert cv.__version__.startswith('4.13.'), cv.__version__
K = np.array([[1000., 0, 640], [0, 1000, 480], [0, 0, 1]])
front = np.array([[1., 0, 0], [0, 0, -1], [0, 1, 0]])
R_VEC = cv.Rodrigues(front)[0]
T_VEC = -front @ np.array([820., -2000., 500.])
local = np.array([[-50., 0, 50], [50, 0, 50], [50, 0, -50], [-50, 0, -50]])


def project(points, r=R_VEC, t=T_VEC):
    return cv.projectPoints(np.asarray(points, np.float32), r, t, K, None)[0]


def solve(points, pixels):
    pts = np.asarray(points, np.float32)
    img = np.asarray(pixels, np.float32)
    planar = any(np.ptp(pts[:, axis]) < 1e-3 for axis in range(3))
    flags = cv.SOLVEPNP_IPPE if planar else cv.SOLVEPNP_ITERATIVE
    ok, r, t = cv.solvePnP(pts, img, K, None, flags=flags)
    if not ok:
        return None
    if len(pts) > 4:
        r, t = cv.solvePnPRefineLM(pts, img, K, None, r, t)
    if np.mean(np.linalg.norm(project(pts, r, t) - img, axis=2)) > 2:
        ok, rr, tt = cv.solvePnP(pts, img, K, None, flags=cv.SOLVEPNP_SQPNP)
        if ok and len(pts) > 4:
            rr, tt = cv.solvePnPRefineLM(pts, img, K, None, rr, tt)
        if ok and np.mean(np.linalg.norm(project(pts, rr, tt) - img, axis=2)) < np.mean(np.linalg.norm(project(pts, r, t) - img, axis=2)):
            r, t = rr, tt
    return r, t


def consensus(objects, images):
    candidates = []
    for ids in [(i,) for i in range(len(objects))] + list(itertools.combinations(range(len(objects)), 2)) + [tuple(range(len(objects)))]:
        rt = solve(np.concatenate([objects[i] for i in ids]), np.concatenate([images[i] for i in ids]))
        if rt is None:
            continue
        errors = [np.max(np.linalg.norm(project(o, *rt) - im, axis=2)) for o, im in zip(objects, images)]
        if len(ids) >= 3 and max(errors) > 1.5:
            continue
        inliers = tuple(i for i, e in enumerate(errors) if np.isfinite(e) and e <= 3)
        if len(inliers) >= max(2, len(objects) // 2 + 1):
            candidates.append((inliers, float(np.mean([errors[i] for i in inliers]))))
    if not candidates:
        return None
    groups = {}
    for ids, error in candidates:
        groups[ids] = min(groups.get(ids, float('inf')), error)
    def better(a, b):
        return b[1] - a[1] >= 0.5 and a[1] <= b[1] * 0.5
    def best(items):
        ordered = sorted(items, key=lambda item: item[1])
        return ordered[0] if len(ordered) == 1 or better(ordered[0], ordered[1]) else None
    size = max(map(len, groups))
    primary = best([item for item in groups.items() if len(item[0]) == size])
    if primary is None:
        return None
    cleaner = [item for item in groups.items() if len(item[0]) < size and better(item, primary)]
    if cleaner:
        count = max(len(item[0]) for item in cleaner)
        selected = best([item for item in groups.items() if len(item[0]) == count])
        return selected[0] if selected else None
    return primary[0]



# Exact front-bottom placement, including a project origin several meters from the tag.
for x in (820, 4000):
    obj = local + [x, 0, 50]
    camera = np.array([x, -1500., 1000.])
    truth_t = -front @ camera
    solved = solve(obj, project(obj, R_VEC, truth_t))
    recovered = (-cv.Rodrigues(solved[0])[0].T @ solved[1]).ravel()
    assert np.linalg.norm(recovered - camera) < 2, recovered
print('PASS front-bottom single-tag camera pose at project X=820 and X=4000 mm')

# Outside-view conventions for all five faces, with printed-up and printed-right axes.
for name, rot, center in [
    ('Front', (0, 0, 0), (820, 0, 470)), ('Back', (0, 0, 180), (820, 930, 470)),
    ('Left', (0, 0, -90), (0, 465, 470)), ('Right', (0, 0, 90), (1640, 465, 470)),
    ('Top', (-90, 0, 0), (820, 465, 940))
]:
    rx, ry, rz = np.deg2rad(rot)
    rot_x = np.array([[1, 0, 0], [0, np.cos(rx), -np.sin(rx)], [0, np.sin(rx), np.cos(rx)]])
    rot_z = np.array([[np.cos(rz), -np.sin(rz), 0], [np.sin(rz), np.cos(rz), 0], [0, 0, 1]])
    obj = local @ (rot_z @ rot_x).T + center
    right = (obj[1] - obj[0]) / 100
    up = (obj[0] - obj[3]) / 100
    forward = np.cross(right, -up)
    R = np.array([right, -up, forward])
    camera = np.array(center) - forward * 1500 + right * 100 + up * 150
    rv, tv = cv.Rodrigues(R)[0], -R @ camera
    solved = solve(obj, project(obj, rv, tv))
    recovered = (-cv.Rodrigues(solved[0])[0].T @ solved[1]).ravel()
    assert np.linalg.norm(recovered - camera) < 2, (name, recovered, camera)
print('PASS outside-view pose on Front, Back, Left, Right and Top')

objects = [local + [x, 0, z] for x, z in [(150, 100), (820, 800), (1500, 100)]]
rng = np.random.default_rng(99)
for shift in (0, 10, 20, 50):
    actual = [p.copy() for p in objects]
    actual[2][:, 0] += shift
    for noise in (0., 0.25, 0.5):
        counts = {}
        for trial in range(100):
            images = [project(p) + rng.normal(0, noise, (4, 1, 2)) for p in actual]
            group = consensus(objects, images)
            counts[str(group)] = counts.get(str(group), 0) + 1
        print(f'shift={shift:2} mm noise={noise:.2f} px groups={counts}')
        if shift == 0 and noise == 0:
            assert counts == {'(0, 1, 2)': 100}
        if shift == 50 and noise == 0:
            assert counts == {'(0, 1)': 100}

if args.official_prints:
    import urllib.request
    for family, filename in [('tag36h11', 'tag36_11_00000.png'), ('tag25h9', 'tag25_09_00000.png'), ('tag16h5', 'tag16_05_00000.png')]:
        url = f'https://raw.githubusercontent.com/AprilRobotics/apriltag-imgs/master/{family}/{filename}'
        data = urllib.request.urlopen(url, timeout=15).read()
        small = cv.imdecode(np.frombuffer(data, np.uint8), cv.IMREAD_GRAYSCALE)
        pixels = cv.resize(small, (600, 600), interpolation=cv.INTER_NEAREST)
        dictionary = cv.aruco.getPredefinedDictionary(getattr(cv.aruco, 'DICT_APRILTAG_' + family[3:].upper()))
        corners, ids, _ = cv.aruco.ArucoDetector(dictionary).detectMarkers(pixels)
        assert ids.ravel().tolist() == [0]
        normalized = np.roll(corners[0][0], -2, axis=0)
        assert normalized[0, 0] < normalized[1, 0] and normalized[0, 1] < normalized[3, 1]
        print(f'PASS official {family} corner normalization: {url}')
