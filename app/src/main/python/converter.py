"""
Wrapper for ebook-converter to be called from Android/Chaquopy.
"""
import os
import sys
import traceback


def convert(input_path, output_path, *extra_args):
    """Convert ebook. Output format determined by extension. Returns dict."""
    saved_argv = sys.argv
    try:
        from ebook_converter.main import main as ec_main

        sys.argv = ['ebook-converter', input_path, output_path] + list(extra_args)
        ec_main()
        return {'success': True, 'message': f'Converted to {output_path}'}
    except SystemExit:
        if os.path.exists(output_path) and os.path.getsize(output_path) > 0:
            return {'success': True, 'message': f'Converted to {output_path}'}
        return {'success': False, 'message': 'Conversion failed (exit)'}
    except Exception as e:
        return {'success': False, 'message': f'{type(e).__name__}: {e}\n{traceback.format_exc()}'}
    finally:
        sys.argv = saved_argv
