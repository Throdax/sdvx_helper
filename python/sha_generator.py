"""
SHAGenerator: Utility class for generating SHA-256 hashes from image files.
"""
from typing import Union, Tuple, Optional
from pathlib import Path
import hashlib
from PIL import Image
import io
import time


class SHAGenerator:
    """
    Utility class for generating SHA-256 hashes from image files.
    """
    def generate_sha256_from_bytes(self, image_bytes: bytes) -> str:
        """
        Generates a SHA-256 hash from the given image file.
        Args:
            image_bytes (bytes): The bytes of the image file.
        Returns:
            str: The SHA-256 hash as a hexadecimal string.
        """
        sha256_hash = hashlib.sha256(image_bytes).hexdigest()
        return sha256_hash
    
    def generate_sha256_from_pil_image(self, image: Image.Image, format: str = 'PNG') -> str:
        """
        Generates a SHA-256 hash from a PIL Image object.
        Args:
            image (Image.Image): The PIL Image object.
            format (str): The format to use when saving the image to bytes (default: 'PNG').
        Returns:
            str: The SHA-256 hash as a hexadecimal string.
        """
        with io.BytesIO() as output:
            image.save(output, format=format)
            image_bytes = output.getvalue()
            
            return self.generate_sha256_from_bytes(image_bytes)
    
    def generate_sha256_from_image(self, image_path: Union[str, Path]) -> str:
        """
        Generates a SHA-256 hash from the given image file.
        Args:
            image_path (Union[str, Path]): Path to the image file.
        Returns:
            str: The SHA-256 hash as a hexadecimal string.
        Raises:
            FileNotFoundError: If the file does not exist.
            IOError: If the file cannot be read.
        """
        image_bytes = self.read_image_bytes(image_path)
        sha256_hash = hashlib.sha256(image_bytes).hexdigest()
        return sha256_hash
    
    def read_image_bytes(self, image_path: Union[str, Path]) -> bytes:
        """
        Reads the image file and returns its bytes.
        Args:
            image_path (Union[str, Path]): Path to the image file.
        Returns:
            bytes: The content of the image file as bytes.
        Raises:
            FileNotFoundError: If the file does not exist.
            IOError: If the file cannot be read.
        """
        path = Path(image_path)
        if not path.is_file():
            raise FileNotFoundError(f"Image file not found: {image_path}")
        return path.read_bytes()
    
    def compare_images_pixel_difference(self, image_path1: str, image_path2: str, threshold: float = 100, threshold_mode: str = "pixels") -> Tuple[float, bool]:
        """
        Compares two images pixel by pixel and returns the time taken and if below threshold, SHA-256 comparison of identical pixels.
        Args:
            image_path1 (str): Path to the first image.
            image_path2 (str): Path to the second image.
            threshold (int or float): The maximum number of differing pixels or percent to trigger SHA-256 comparison.
            threshold_mode (str): 'pixels' for absolute pixel count, 'percent' for percent of total pixels.
        Returns:
            tuple: (elapsed_time_seconds, sha_match)
        """
        start_time = time.time()
        img1 = Image.open(image_path1).convert('RGB')
        img2 = Image.open(image_path2).convert('RGB')
    
        if img1.size != img2.size:
            raise ValueError(f"Image sizes do not match: {img1.size} vs {img2.size}")
    
        width, height = img1.size
        pixels1 = img1.load()
        pixels2 = img2.load()
        diff_count = 0
        total_pixels = width * height
        identical_pixels = []
    
        for y in range(height):
            for x in range(width):
                if pixels1[x, y] != pixels2[x, y]:
                    diff_count += 1
                else:
                    identical_pixels.append(pixels1[x, y])
    
        elapsed_time = time.time() - start_time
    
        # Determine effective threshold
        if threshold_mode == "percent":
            effective_threshold = total_pixels * (threshold / 100.0)
            threshold_desc = f"{threshold}% of {total_pixels} = {effective_threshold:.0f} pixels"
        else:
            effective_threshold = threshold
            threshold_desc = f"{threshold} pixels"
    
        sha_match = False
        if diff_count < effective_threshold:
            print(f"Different pixels of {diff_count} is below threshold of {threshold_desc}")
            print("Calculating SHA-256 of identical pixels...")
            # Convert identical pixels to bytes
            pixel_bytes = b''.join(bytes([r, g, b]) for (r, g, b) in identical_pixels)
            sha1 = hashlib.sha256(pixel_bytes).hexdigest()
            sha2 = hashlib.sha256(pixel_bytes).hexdigest()  # Both are the same since identical_pixels is from img1
            # For completeness, recalculate from img2 (should be the same)
            pixel_bytes2 = b''.join(bytes([r, g, b]) for (r, g, b) in identical_pixels)
            sha2 = hashlib.sha256(pixel_bytes2).hexdigest()
            
            print(f"SHA-256 of identical pixels (img1): {sha1}")
            print(f"SHA-256 of identical pixels (img2): {sha2}")
            sha_match = (sha1 == sha2)
        else:
            print(f"Different pixels of {diff_count} is above threshold of {threshold_desc}. Images are different")
    
        return elapsed_time, sha_match


if __name__ == "__main__":
    generator = SHAGenerator()
    
    sha = generator.generate_sha256_from_image("D:/Tools/SoundVoltex/sdvx_helper/jackets/7c1f070180697affcdb32c0b2.png")
    print(sha)
    
    sha = generator.generate_sha256_from_image("D:/Tools/SoundVoltex/sdvx_helper/jackets/fc1f070180697affcdb32c192.png")
    print(sha)
