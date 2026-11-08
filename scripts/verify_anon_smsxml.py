import xml.etree.ElementTree as ET
import html
import re
from difflib import SequenceMatcher

def extract_sensitive_data(text):
    """Extract all sensitive data patterns from text"""
    if not text:
        return {}
    
    text = html.unescape(text)
    
    patterns = {
        'account_numbers': re.findall(r'XXXX\d{4}', text),
        'amounts_aed': re.findall(r'AED \d+\.\d+', text),
        'amounts_usd': re.findall(r'USD \d+\.\d+', text),
        'amounts_thb': re.findall(r'THB \d+\.\d+', text),
        'phone_numbers': re.findall(r'\+971\d{9}', text),
        'otp_codes': re.findall(r'\b\d{6}\b', text),
        'dates': re.findall(r'\d{2}/\d{2}/\d{2,4}', text),
        'times': re.findall(r'\d{2}:\d{2}', text),
        'merchant_names': []
    }
    
    # Extract merchant names (lines with location codes)
    lines = text.split('\n')
    for line in lines:
        if any(loc in line for loc in ['DUBAI', 'SHARJAH', 'BANGKOK', 'AE', 'US', 'TH', 'SG']):
            if len(line) > 20:
                patterns['merchant_names'].append(line.strip())
    
    return patterns

def compare_messages(original_file, anonymized_file):
    """Compare original and anonymized XML files"""
    
    # Parse both files
    orig_tree = ET.parse(original_file)
    anon_tree = ET.parse(anonymized_file)
    
    orig_root = orig_tree.getroot()
    anon_root = anon_tree.getroot()
    
    print("=" * 80)
    print("SMS BACKUP ANONYMIZATION COMPARISON REPORT")
    print("=" * 80)
    print()
    
    # Compare backup set
    print("1. BACKUP SET ID:")
    print(f"   Original:    {orig_root.get('backup_set')}")
    print(f"   Anonymized:  {anon_root.get('backup_set')}")
    print(f"   ✓ Changed: {orig_root.get('backup_set') != anon_root.get('backup_set')}")
    print()
    
    # Get all SMS messages
    orig_sms = orig_root.findall('.//sms')
    anon_sms = anon_root.findall('.//sms')
    
    print(f"2. SMS MESSAGE COUNT:")
    print(f"   Original:    {len(orig_sms)}")
    print(f"   Anonymized:  {len(anon_sms)}")
    print(f"   ✓ Match: {len(orig_sms) == len(anon_sms)}")
    print()
    
    # Compare a sample of messages
    print("3. SAMPLE MESSAGE COMPARISON:")
    print("-" * 80)
    
    sample_size = min(5, len(orig_sms))
    
    for i in range(sample_size):
        orig_body = orig_sms[i].get('body')
        anon_body = anon_sms[i].get('body')
        
        print(f"\n   Message {i+1}:")
        print(f"   {'─' * 76}")
        
        # Extract sensitive data
        orig_data = extract_sensitive_data(orig_body)
        anon_data = extract_sensitive_data(anon_body)
        
        # Compare account numbers
        if orig_data['account_numbers']:
            print(f"   Account Numbers:")
            print(f"      Original:    {orig_data['account_numbers']}")
            print(f"      Anonymized:  {anon_data['account_numbers']}")
            print(f"      ✓ Changed: {set(orig_data['account_numbers']) != set(anon_data['account_numbers'])}")
        
        # Compare amounts
        if orig_data['amounts_aed']:
            print(f"   AED Amounts:")
            print(f"      Original:    {orig_data['amounts_aed']}")
            print(f"      Anonymized:  {anon_data['amounts_aed']}")
            print(f"      ✓ Changed: {set(orig_data['amounts_aed']) != set(anon_data['amounts_aed'])}")
        
        # Compare phone numbers
        if orig_data['phone_numbers']:
            print(f"   Phone Numbers:")
            print(f"      Original:    {orig_data['phone_numbers']}")
            print(f"      Anonymized:  {anon_data['phone_numbers']}")
            print(f"      ✓ Changed: {set(orig_data['phone_numbers']) != set(anon_data['phone_numbers'])}")
        
        # Compare OTP codes
        if orig_data['otp_codes']:
            print(f"   OTP Codes:")
            print(f"      Original:    {orig_data['otp_codes']}")
            print(f"      Anonymized:  {anon_data['otp_codes']}")
            print(f"      ✓ Changed: {set(orig_data['otp_codes']) != set(anon_data['otp_codes'])}")
        
        # Compare merchant names
        if orig_data['merchant_names']:
            print(f"   Merchant Names:")
            for orig_merchant in orig_data['merchant_names'][:2]:  # Show first 2
                print(f"      Original:    {orig_merchant}")
            if anon_data['merchant_names']:
                for anon_merchant in anon_data['merchant_names'][:2]:
                    print(f"      Anonymized:  {anon_merchant}")
            print(f"      ✓ Changed: {set(orig_data['merchant_names']) != set(anon_data['merchant_names'])}")
        
        # Show similarity percentage
        similarity = SequenceMatcher(None, orig_body, anon_body).ratio()
        print(f"\n   Similarity: {similarity*100:.1f}% (should be low for good anonymization)")
    
    print()
    print("-" * 80)
    
    # Detailed sensitive data scan
    print("\n4. COMPREHENSIVE SENSITIVE DATA SCAN:")
    print("-" * 80)
    
    all_orig_accounts = set()
    all_anon_accounts = set()
    all_orig_amounts = set()
    all_anon_amounts = set()
    all_orig_phones = set()
    all_anon_phones = set()
    
    for orig, anon in zip(orig_sms, anon_sms):
        orig_data = extract_sensitive_data(orig.get('body'))
        anon_data = extract_sensitive_data(anon.get('body'))
        
        all_orig_accounts.update(orig_data['account_numbers'])
        all_anon_accounts.update(anon_data['account_numbers'])
        all_orig_amounts.update(orig_data['amounts_aed'])
        all_anon_amounts.update(anon_data['amounts_aed'])
        all_orig_phones.update(orig_data['phone_numbers'])
        all_anon_phones.update(anon_data['phone_numbers'])
    
    print(f"\n   Unique Account Numbers:")
    print(f"      Original:    {len(all_orig_accounts)} unique values")
    print(f"      Anonymized:  {len(all_anon_accounts)} unique values")
    print(f"      Overlap:     {len(all_orig_accounts & all_anon_accounts)} values")
    print(f"      ✓ Status: {'PASS - No overlap' if len(all_orig_accounts & all_anon_accounts) == 0 else '✗ FAIL - Data leaked!'}")
    
    print(f"\n   Unique AED Amounts:")
    print(f"      Original:    {len(all_orig_amounts)} unique values")
    print(f"      Anonymized:  {len(all_anon_amounts)} unique values")
    print(f"      Overlap:     {len(all_orig_amounts & all_anon_amounts)} values")
    print(f"      ✓ Status: {'PASS - No overlap' if len(all_orig_amounts & all_anon_amounts) == 0 else '✗ FAIL - Data leaked!'}")
    
    print(f"\n   Unique Phone Numbers:")
    print(f"      Original:    {len(all_orig_phones)} unique values")
    print(f"      Anonymized:  {len(all_anon_phones)} unique values")
    print(f"      Overlap:     {len(all_orig_phones & all_anon_phones)} values")
    print(f"      ✓ Status: {'PASS - No overlap' if len(all_orig_phones & all_anon_phones) == 0 else '✗ FAIL - Data leaked!'}")
    
    # Check MMS messages
    orig_mms = orig_root.findall('.//mms')
    anon_mms = anon_root.findall('.//mms')
    
    print(f"\n5. MMS MESSAGE COUNT:")
    print(f"   Original:    {len(orig_mms)}")
    print(f"   Anonymized:  {len(anon_mms)}")
    print(f"   ✓ Match: {len(orig_mms) == len(anon_mms)}")
    
    # Check transaction IDs
    if orig_mms:
        print(f"\n6. TRANSACTION IDs (MMS):")
        for i, (orig, anon) in enumerate(zip(orig_mms[:3], anon_mms[:3])):
            orig_tr = orig.get('tr_id')
            anon_tr = anon.get('tr_id')
            print(f"   Message {i+1}:")
            print(f"      Original:    {orig_tr}")
            print(f"      Anonymized:  {anon_tr}")
            print(f"      ✓ Changed: {orig_tr != anon_tr}")
    
    print()
    print("=" * 80)
    print("ANONYMIZATION REPORT COMPLETE")
    print("=" * 80)

if __name__ == "__main__":
    original_file = '../parser-core/src/test/resources/fab_sms_test_data.xml'
    anonymized_file = "../parser-core/src/test/resources/fab_sms_test_data_anonymized.xml"
    try:
        compare_messages(original_file, anonymized_file)
    except FileNotFoundError as e:
        print(f"Error: Could not find file - {e}")
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()