#!/usr/bin/env python3
"""Generate the v1.0.22 extra60 pose sheets: 4 sheets x 5 poses for
each of the six dancers (modern_woman Y1 was generated interactively
and is already in place). Same route as extra40: media-generation
with the dancer's approved reference image + a text prompt asking
for the SAME character five times in a row on solid dark navy.

Outputs sheet_<key>_Y<n>.png (2480x992-class landscape sheets are
2048x1152 from this endpoint; the cutout pipeline normalises by
content height, so the absolute size does not matter).
"""
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent  # ~/workspace/shield-karaoke
SINGLES = ROOT / "app/src/main/res/drawable-nodpi"
SUMO_MASTER = (
    ROOT
    / "dancers/media-generation-mysing-dancer-sumo-design-v5-0-7724d18d-10f5-4420-8024-7625741b884a.webp"
)

COMMON_TAIL = (
    " The five figures stand in one horizontal row, evenly spaced, all exactly the same size, "
    "full body visible from head to feet, feet near the bottom edge, figures do not touch or "
    "overlap, solid dark navy-blue background, clean even studio lighting, Pixar-style 3D "
    "animation render."
)

CHARACTERS = {
    "modern_woman": (
        SINGLES / "dancer_modern_woman.png",
        "a young woman with long wavy blonde hair, big blue eyes, small gold hoop earrings, "
        "white short-sleeve crop top, white mini skirt and black heeled ankle boots",
    ),
    "ancient_woman": (
        SINGLES / "dancer_ancient_woman.png",
        "a young ancient-Chinese woman with black hair in a high bun with pink flower hairpins, "
        "wearing a pink embroidered hanfu with long sheer pale-pink gauze wide sleeves and a "
        "flowing sheer layered skirt, pink sash, white cloth shoes",
    ),
    "sumo": (
        SUMO_MASTER,
        "a huge Japanese sumo wrestler with a chonmage topknot, bare chest and a big round belly, "
        "wearing ONLY a sumo mawashi: a thick GRAY fabric belt wrapped around his waist and hips "
        "with a plain WHITE rectangular front apron panel hanging down the front centre of the "
        "belt and short white tassel strings, bare legs and bare feet. The belt must be gray and "
        "the front panel must be white in every figure",
    ),
    "ancient_girl": (
        SINGLES / "dancer_ancient_girl.png",
        "a cute little ancient-Chinese girl of about five with black hair in two round buns with "
        "small yellow flower ornaments and tassels, wearing a yellow hanfu top with frog buttons "
        "and an embroidered yellow skirt over white pants, yellow shoes",
    ),
    "baby": (
        SINGLES / "dancer_baby.png",
        "a chubby happy baby with a little wispy light-brown hair, big eyes, wearing ONLY a white "
        "diaper, barefoot, standing and dancing like a toddler",
    ),
    "robot": (
        SINGLES / "dancer_robot.png",
        "a friendly glossy white-and-silver humanoid robot with a round dome head, a black face "
        "screen showing a glowing cyan-blue LED smiley face (two round eyes and a smile), a chest "
        "panel with four small blue lights, articulated metal arms and legs and white shoe-like feet",
    ),
}

POSES = {
    "modern_woman": {
        2: [
            "disco pose, right arm pointing diagonally up, left arm pointing diagonally down, hips cocked",
            "hip-sway groove, both hands on her hips, weight shifted to one leg, hair swinging",
            "running-man dance step, one knee lifted high, arms pumping opposite",
            "hair-flip pose, one hand tossing her long hair back, other arm extended to the side",
            "marching knee-lift, both arms pumping at her sides, chin up, big smile",
        ],
        3: [
            "jumping jack frozen mid-air, arms and legs spread in an X",
            "leaning back cool pose, both hands making finger guns pointing forward",
            "low twirl, body turned sideways mid-spin, skirt flaring, one arm trailing",
            "high side kick with the left leg, both arms out for balance",
            "deep side lunge reaching one arm far to the side, other hand on her knee",
        ],
        4: [
            "salsa cross-step, legs crossed, one arm curled overhead",
            "shoulder shimmy leaning forward, elbows bent, playful grin",
            "back kick looking over her shoulder, one leg kicked behind, hands on hips",
            "chest-pop pose, arms crossed in front of her chest, knees bent in rhythm",
            "grand finish pose on tiptoe, one arm stretched to the sky, other arm out to the side",
        ],
    },
    "ancient_woman": {
        1: [
            "graceful spin, long gauze sleeves and skirt swirling around her",
            "flying-apsaras pose, one sleeve raised in an arc overhead, the other extended to the side, one leg lifted behind",
            "standing on one leg like a crane, sleeves draped down softly, serene smile",
            "gentle greeting bow, hands together at her waist, sleeves hanging in two long veils",
            "both arms waving wide, sleeves billowing like wings, stepping sideways",
        ],
        2: [
            "slow turn with her sheer skirt flaring out in a bell",
            "one water-sleeve tossed high upward in an arc, head tilted up watching it",
            "one leg lifted behind her, arms in a soft round circle overhead",
            "low curtsey bend, one knee deeply bent, sleeves pooling toward the floor",
            "stepping turn with sleeves wrapped around her body, looking back over her shoulder",
        ],
        3: [
            "water-sleeve flicked far to the left, body following the motion",
            "water-sleeve flicked far to the right, head turned in profile",
            "gentle backbend, one sleeve trailing behind her like a ribbon",
            "lotus hands pressed together at her chest, one foot stepped to the side",
            "sleeve drawn across her face in a shy peek, one eye visible, giggling",
        ],
        4: [
            "grand overhead sleeve circle, both arms raised making a full moon shape",
            "cross-step curtsey, one leg crossed behind, sleeves lowered gracefully",
            "gliding pose, arms spread like a bird's wings, one foot pointed forward",
            "one sleeve raised high in a diagonal line, the other low, a classic dance finish",
            "joyful hop on one foot, sleeves fluttering up around her",
        ],
    },
    "sumo": {
        1: [
            "shiko leg lift: left leg raised very high to the side, both arms down gripping his mawashi, fierce face",
            "shiko leg lift: right leg raised very high to the side, both arms down gripping his mawashi, fierce face",
            "salt-throwing pose, one hand raised high scattering salt, other hand holding a pinch at his chest",
            "chest-out roar, both arms flexed like a strongman, mouth open shouting",
            "deep sumo squat, legs wide, hands resting on his knees, glaring forward",
        ],
        2: [
            "ring-entrance hand clap, palms pressed together at chest height, legs in a wide squat",
            "one open palm striking forward in a thrust, other hand at his hip, one foot stepped forward",
            "heavy stomping march, left foot raised mid-stomp, arms swinging low",
            "heavy stomping march, right foot raised mid-stomp, arms swinging low",
            "wide crouch with both arms spread far out to the sides like a wall",
        ],
        3: [
            "deep respectful bow, torso bent forward, arms hanging straight down",
            "one arm raised high in a chopping pose, other arm across his belly, legs wide",
            "heavy side lunge leaning left, both hands on his left knee",
            "heavy side lunge leaning right, both hands on his right knee",
            "hands on knees glare stance, elbows out, leaning forward intimidatingly",
        ],
        4: [
            "victory pose, both thick arms raised to the sky, big grin",
            "wobbly one-leg balance, arms windmilling slightly, surprised face",
            "one huge arm sweeping in a big circle, body turning with it",
            "small sumo hop, both feet just off the ground, arms down at his sides, belly bouncing",
            "one hand on his chest in a proud finish pose, chin up, legs in a wide stance",
        ],
    },
    "ancient_girl": {
        1: [
            "hopping on one foot, arms spread out for balance, laughing",
            "twirling around, her little skirt flaring, arms slightly raised",
            "both hands making a big heart shape above her head, on tiptoe",
            "skipping step mid-air, one knee up, arms swinging",
            "peek-a-boo pose, both hands beside her face with fingers spread, giggling",
        ],
        2: [
            "happy jump with both feet off the ground, arms up in a V",
            "spinning like a top, arms tucked, skirt swirling",
            "waving both hands hello, bouncing on her toes",
            "reaching high on tiptoe with both arms, trying to catch something above",
            "crouching low then springing up, arms thrown wide",
        ],
        3: [
            "side gallop step, body leaning sideways, arms like airplane wings",
            "hands pressed to her cheeks in a cute surprised pose, one foot lifted",
            "little front kick, arms out for balance, determined cute face",
            "curtsey bow, holding her skirt sides, head bowed with a smile",
            "marching step with high knees, arms swinging proudly",
        ],
        4: [
            "star jump mid-air, arms and legs spread wide",
            "pointing both index fingers forward in a dance move, hips swaying",
            "swaying side to side with hands clasped in front, humming happily",
            "heel-toe tap dance pose, one toe pointed at the floor, hands on hips",
            "finish pose: arms wide open and a happy bow",
        ],
    },
    "baby": {
        1: [
            "toddling step to the left, both chubby arms raised high, delighted laugh",
            "toddling step to the right, both chubby arms raised high, delighted laugh",
            "airplane wobble, arms stretched out to the sides, body tilting, giggling",
            "clapping his little hands together at chest height, bouncing on his feet",
            "on tiptoe reaching both arms up to the sky, mouth open in wonder",
        ],
        2: [
            "waddling sideways to the left, arms swinging, big grin",
            "waddling sideways to the right, arms swinging, big grin",
            "waving both hands excitedly, bouncing up and down",
            "hands patting his round belly, laughing with eyes squeezed shut",
            "one chubby foot lifted in a wobbly balance, arms windmilling",
        ],
        3: [
            "marching in place with high chubby knees, arms pumping",
            "walking waddle with arms swinging wide, looking proud",
            "surprised pose, both hands on his cheeks, mouth in a little O",
            "blowing a kiss with one hand, other arm out to the side",
            "tiny jump with both feet off the ground, arms up, thrilled face",
        ],
        4: [
            "wobbly spin, arms out, dizzy happy face",
            "pointing one finger and giggling, other hand on his belly",
            "both arms up in a victory V, standing tall and proud",
            "little bow wobbling forward, arms dangling, smiling",
            "stomping one foot in rhythm, fists pumped at his sides, cheering face",
        ],
    },
    "robot": {
        1: [
            "classic robot dance: one arm raised with a stiff 90-degree elbow bend, other arm straight down, one leg stepped out",
            "stiff marching step, arms swinging in rigid straight lines, one knee up",
            "arms bent into square right angles in front of its chest, head tilted, mechanical pose",
            "torso twisted to the side, both arms stretched out horizontally in opposite directions",
            "stiff straight-leg kick forward, arms pinned in angular poses",
        ],
        2: [
            "salute pose, one arm bent stiffly to its dome head, standing at attention",
            "piston arms: left arm punched up, right arm punched down, legs in a wide stance",
            "side-step slide, arms pumping like pistons at its sides, leaning into the step",
            "head tilted 30 degrees with a curious LED face, both arms outstretched sideways",
            "moonwalk slide pose, one leg stretched back on its toe, body leaning forward, arms swinging",
        ],
        3: [
            "arms forming a triangle above its head, legs crossed in a mechanical stance",
            "deep robot squat, knees bent at sharp angles, arms hanging in stiff segments",
            "one arm extended pointing straight forward, other arm bent across its chest, one leg back",
            "one-leg balance, arms spread like a windmill frozen mid-turn",
            "wind-up spin pose, body half-turned, arms trailing behind in stiff angles",
        ],
        4: [
            "disco point: one arm diagonally up, the other diagonally down, hips shifted, LED face winking",
            "celebration pose, both arms raised high, one knee lifted, happy LED face",
            "mechanical bow, torso hinged forward at the waist, arms hanging straight and stiff",
            "running robot pose leaning far forward, arms pumping, one leg stretched behind",
            "hero finish pose, one fist pumped to the sky, other hand on its hip, chest lights glowing",
        ],
    },
}


def generate(key: str, sheet_no: int) -> Path:
    ref, desc = CHARACTERS[key]
    poses = POSES[key][sheet_no]
    pose_text = "; ".join(f"{i + 1}) {p}" for i, p in enumerate(poses))
    prompt = (
        f"Character pose sheet, Pixar-style 3D animation render: the exact same character as the "
        f"reference image — {desc} — shown FIVE times in a row in five brand-new dance poses on a "
        f"solid dark navy-blue background: {pose_text}.{COMMON_TAIL}"
    )
    conv = json.dumps([{"image": str(ref)}, {"text": prompt}])
    out = HERE / f"sheet_{key}_Y{sheet_no}.png"
    if out.exists():
        print(f"skip {out.name} (exists)")
        return out
    res = subprocess.run(
        [
            "/opt/hatch/bin/media-generation",
            "--media-subagent-output-type", "image",
            "--conversation-json", conv,
            "--orientation", "landscape",
            "--timeout-secs", "600",
            "--output-dir", str(HERE / "gen"),
        ],
        capture_output=True, text=True, timeout=700,
    )
    tail = (res.stdout or "").strip().splitlines()
    data = json.loads(tail[-1]) if tail else {}
    paths = data.get("media_paths") or []
    if not paths:
        raise RuntimeError(f"{key} Y{sheet_no}: no media path; stdout tail: {tail[-3:] if tail else res.stderr[-500:]}")
    src = Path(paths[0])
    Image.open(src).convert("RGB").save(out)
    print(f"ok {out.name} from {src.name}")
    return out


def main() -> None:
    jobs = []
    for key, sheets in POSES.items():
        for no in sheets:
            jobs.append((key, no))
    if len(sys.argv) > 1:  # optional filter: key or key:Y<n>
        want = sys.argv[1]
        jobs = [j for j in jobs if j[0] == want or f"{j[0]}:Y{j[1]}" == want]
    failed = []
    for key, no in jobs:
        try:
            generate(key, no)
        except Exception as e:  # keep going; report at the end
            failed.append((key, no, str(e)[:200]))
            print(f"FAIL {key} Y{no}: {e}")
    print(f"done: {len(jobs) - len(failed)}/{len(jobs)} ok; failed: {failed}")


if __name__ == "__main__":
    main()
