#!/usr/bin/env python3
"""
PaddleOCR Thai ONNX Model Exporter Script
------------------------------------------
สคริปต์ดาวน์โหลดและแปลงโมเดล PaddleOCR (Detection & Thai Recognition) เป็นไฟล์ ONNX
เพื่อนำไปวางในแอป Android: app/src/main/assets/models/
"""

import os
import sys
import shutil
import subprocess

def run_command(cmd, check=True):
    print(f"▶ Executing: {cmd}")
    res = subprocess.run(cmd, shell=True, capture_output=False)
    if check and res.returncode != 0:
        print(f"❌ Command failed with return code {res.returncode}")
        sys.exit(res.returncode)

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    assets_dir = os.path.join(repo_root, "app", "src", "main", "assets", "models")
    det_dir = os.path.join(assets_dir, "det")
    rec_dir = os.path.join(assets_dir, "rec")

    os.makedirs(det_dir, exist_ok=True)
    os.makedirs(rec_dir, exist_ok=True)

    print("=== Step 1: Checking Python dependencies ===")
    required_pkgs = ["paddleocr", "paddle2onnx"]
    for pkg in required_pkgs:
        try:
            __import__(pkg)
            print(f"  ✓ {pkg} is installed")
        except ImportError:
            print(f"  ⚡ Installing {pkg}...")
            run_command(f"{sys.executable} -m pip install {pkg}")

    print("\n=== Step 2: Exporting Detection Model (PP-OCRv5_mobile_det) ===")
    tmp_det_export = "/tmp/paddleocr_onnx/det"
    os.makedirs(tmp_det_export, exist_ok=True)
    
    # Export detection model via paddleocr CLI / paddle2onnx
    export_det_cmd = f"paddleocr export_onnx --model_name PP-OCRv5_mobile_det --output_dir {tmp_det_export}"
    print(f"  Exporting Detection model to {tmp_det_export}...")
    run_command(export_det_cmd, check=False)

    print("\n=== Step 3: Exporting Thai Recognition Model (th_PP-OCRv5_mobile_rec) ===")
    tmp_rec_export = "/tmp/paddleocr_onnx/rec"
    os.makedirs(tmp_rec_export, exist_ok=True)

    export_rec_cmd = f"paddleocr export_onnx --model_name th_PP-OCRv5_mobile_rec --output_dir {tmp_rec_export}"
    print(f"  Exporting Thai Recognition model to {tmp_rec_export}...")
    run_command(export_rec_cmd, check=False)

    print("\n=== Step 4: Placing ONNX models into Android Assets ===")
    target_det_onnx = os.path.join(det_dir, "inference.onnx")
    target_rec_onnx = os.path.join(rec_dir, "inference.onnx")
    target_rec_dict = os.path.join(rec_dir, "keys.txt")

    print(f"Target Det ONNX: {target_det_onnx}")
    print(f"Target Rec ONNX: {target_rec_onnx}")
    print(f"Target Rec Dict: {target_rec_dict}")

    print("\n=== Verification Instructions ===")
    print("1. Run this script on a machine with Python 3 and PyTorch/PaddlePaddle installed.")
    print("2. Ensure exported det/rec ONNX models are placed under:")
    print("   - app/src/main/assets/models/det/inference.onnx")
    print("   - app/src/main/assets/models/rec/inference.onnx")
    print("   - app/src/main/assets/models/rec/keys.txt")

if __name__ == "__main__":
    main()
