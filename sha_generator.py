"""
SHAGenerator: Utility class for generating SHA-256 hashes from image files.
"""
from typing import Union
from pathlib import Path
import hashlib

class SHAGenerator:
    """
    Utility class for generating SHA-256 hashes from image files.
    """
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


if __name__ == "__main__":
    generator = SHAGenerator()
    
    sha = generator.generate_sha256_from_image("D:/Tools/SoundVoltex/sdvx_helper/jackets/7c1f070180697affcdb32c0b2.png")
    print(sha)
    
    sha = generator.generate_sha256_from_image("D:/Tools/SoundVoltex/sdvx_helper/jackets/fc1f070180697affcdb32c192.png")
    print(sha)
