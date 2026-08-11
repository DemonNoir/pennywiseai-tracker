import xml.etree.ElementTree as ET
import random
import string
import html

def random_phone():
    """Generate random UAE phone number"""
    return f"+971{random.randint(500000000, 599999999)}"

def random_account():
    """Generate random account number"""
    return f"XXXX{random.randint(1000, 9999)}"

def random_card():
    """Generate random card number"""
    return f"XXXX{random.randint(1000, 9999)}"

def random_amount(currency="AED"):
    """Generate random amount"""
    return f"{currency} {random.uniform(10.00, 999.99):.2f}"

def random_balance():
    """Generate random balance"""
    return f"{random.uniform(1000.00, 50000.00):.2f}"

def random_merchant():
    """Generate random merchant name"""
    merchants = [
        "RANDOM CAFE", "GENERIC STORE", "FOOD PLACE", "SHOP CENTER",
        "RETAIL OUTLET", "MARKET PLACE", "DINING SPOT", "SERVICE CENTER",
        "EXPRESS MART", "QUICK STOP", "DAILY NEEDS", "FRESH MARKET",
        "ONLINE STORE", "COFFEE HOUSE", "RESTAURANT", "FUEL STATION"
    ]
    return random.choice(merchants).ljust(22)

def random_location():
    """Generate random location"""
    locations = ["DUBAI", "SHARJAH", "ABU DHABI", "AJMAN", "AL AIN", "FUJAIRAH"]
    return random.choice(locations).ljust(16)

def random_country():
    """Generate random country code"""
    countries = ["AE", "US", "GB", "SG", "TH"]
    return random.choice(countries)

def random_otp():
    """Generate random 6-digit OTP"""
    return str(random.randint(100000, 999999))

def anonymize_body_text(body):
    """Completely anonymize the body text"""
    if not body:
        return body
    
    # Decode HTML entities
    body = html.unescape(body)
    lines = body.strip().split('\n')
    new_lines = []
    
    for line in lines:
        line = line.strip()
        
        # Skip empty lines
        if not line:
            new_lines.append('')
            continue
        
        # Header lines - keep as is
        if line in ['Debit Card Purchase', 'Debit', 'Credit', 'ATM Cash withdrawal', 
                    'Outward Remittance', 'Inward Remittance']:
            new_lines.append(line)
            continue
        
        # Account line
        if line.startswith('Account'):
            new_lines.append(f'Account {random_account()}')
            continue
        
        # Card line
        if line.startswith('Card'):
            new_lines.append(f'Card {random_card()}')
            continue
        
        # Amount lines (AED, USD, THB, etc.)
        if line.startswith('AED'):
            new_lines.append(random_amount('AED'))
            continue
        if line.startswith('USD'):
            new_lines.append(random_amount('USD'))
            continue
        if line.startswith('THB'):
            new_lines.append(random_amount('THB'))
            continue
        
        # Merchant line (long lines with location info)
        if len(line) > 25 and any(loc in line for loc in ['DUBAI', 'SHARJAH', 'AE', 'US', 'TH', 'SG', 'BANGKOK']):
            country = random_country()
            new_lines.append(f'{random_merchant()}{random_location()}{country}')
            continue
        
        # Date/time line
        if '/' in line and ':' in line:
            # Keep date format but randomize
            parts = line.split()
            if len(parts) >= 2:
                date_part = parts[0]  # DD/MM/YY
                time_part = parts[1]  # HH:MM
                # Generate random date/time
                day = random.randint(1, 28)
                month = random.randint(1, 12)
                year = random.randint(23, 25)
                hour = random.randint(0, 23)
                minute = random.randint(0, 59)
                new_lines.append(f'{day:02d}/{month:02d}/{year:02d} {hour:02d}:{minute:02d}')
                continue
        
        # Balance line
        if 'Available Balance' in line:
            new_lines.append(f'Available Balance AED {random_balance()}')
            continue
        
        # Value Date line
        if 'Value Date' in line:
            day = random.randint(1, 28)
            month = random.randint(1, 12)
            year = random.randint(2023, 2025)
            new_lines.append(f'Value Date {day:02d}/{month:02d}/{year}')
            continue
        
        # OTP message lines - completely rewrite
        if 'OTP' in line or 'Dear Customer' in line or 'DO NOT DISCLOSE' in line:
            if 'DO NOT DISCLOSE' in line:
                new_lines.append('DO NOT DISCLOSE YOUR OTP CODE TO ANYONE.')
            elif random_otp() in line or any(char.isdigit() for char in line):
                # Replace entire line with anonymized version
                otp = random_otp()
                amount = random_amount('AED')
                card = random_card()
                merchant = random.choice(['Online Store', 'Retail Shop', 'Food Delivery'])
                new_line = line
                # Replace OTP codes
                import re
                new_line = re.sub(r'\b\d{6}\b', otp, new_line)
                # Replace amounts
                new_line = re.sub(r'AED \d+\.\d+', amount, new_line)
                # Replace card numbers
                new_line = re.sub(r'\d{4}', card.split('XXXX')[1], new_line)
                # Replace phone numbers
                new_line = re.sub(r'\+971[\d\s]+', random_phone(), new_line)
                # Replace dates
                new_line = re.sub(r'\d{2}-\d{2}-\d{4}', f'{random.randint(1,28):02d}-{random.randint(1,12):02d}-{random.randint(2023,2025)}', new_line)
                # Replace times
                new_line = re.sub(r'\d{2}:\d{2} [AP]M', f'{random.randint(1,12):02d}:{random.randint(0,59):02d} {"AM" if random.random() > 0.5 else "PM"}', new_line)
                # Replace merchant names
                for merchant_name in ['Deliveroo', 'DELIVEROO', 'Samsung Pay']:
                    new_line = new_line.replace(merchant_name, random.choice(['Online Store', 'Digital Service', 'Payment App']))
                new_lines.append(new_line)
            else:
                new_lines.append(line)
            continue
        
        # IBAN/Account transfer lines
        if 'IBAN' in line or 'transfer' in line.lower():
            import re
            new_line = line
            new_line = re.sub(r'AED \d+\.\d+', random_amount('AED'), new_line)
            new_line = re.sub(r'XXXX\d{4}', random_account(), new_line)
            new_line = re.sub(r'\d{2}/\d{2}/\d{4}', f'{random.randint(1,28):02d}/{random.randint(1,12):02d}/{random.randint(2023,2025)}', new_line)
            new_line = re.sub(r'\d{2}:\d{2}', f'{random.randint(0,23):02d}:{random.randint(0,59):02d}', new_line)
            new_lines.append(new_line)
            continue
        
        # Unsuccessful transaction line
        if 'unsuccessful transaction' in line.lower():
            import re
            new_line = line
            new_line = re.sub(r'AED \d+\.\d+', random_amount('AED'), new_line)
            new_line = re.sub(r'XXXX\d{4}', random_account(), new_line)
            new_line = re.sub(r'\d{2}/\d{2}/\d{2}', f'{random.randint(1,28):02d}/{random.randint(1,12):02d}/{random.randint(23,25):02d}', new_line)
            new_line = re.sub(r'\d{2}:\d{2}', f'{random.randint(0,23):02d}:{random.randint(0,59):02d}', new_line)
            new_lines.append(new_line)
            continue
        
        # Default - keep line as is (or add more specific handling)
        new_lines.append(line)
    
    # Re-encode HTML entities for XML
    result = '\n'.join(new_lines)
    return result

def anonymize_xml(input_file, output_file):
    """Main function to anonymize SMS backup XML"""
    
    # Parse the XML
    tree = ET.parse(input_file)
    root = tree.getroot()
    
    # Anonymize backup_set ID
    root.set('backup_set', ''.join(random.choices(string.hexdigits.lower(), k=36)))
    
    # Anonymize all SMS messages
    for sms in root.findall('.//sms'):
        body = sms.get('body')
        if body:
            sms.set('body', anonymize_body_text(body))
        
        # Anonymize service center
        if sms.get('service_center'):
            sms.set('service_center', random_phone())
        
        # Anonymize address (bank name can stay or change)
        # sms.set('address', 'BANK')
    
    # Anonymize MMS messages
    for mms in root.findall('.//mms'):
        # Anonymize transaction ID
        if mms.get('tr_id'):
            mms.set('tr_id', f"T{''.join(random.choices(string.hexdigits.lower(), k=8))}")
        
        # Anonymize text in parts
        for part in mms.findall('.//part'):
            text = part.get('text')
            if text:
                part.set('text', anonymize_body_text(text))
    
    # Write output
    tree.write(output_file, encoding='UTF-8', xml_declaration=True)
    print(f"✓ Anonymization complete!")
    print(f"✓ Output saved to: {output_file}")
    print(f"✓ Processed {len(root.findall('.//sms'))} SMS and {len(root.findall('.//mms'))} MMS messages")
# Usage
if __name__ == "__main__":
    input_file = '../parser-core/src/test/resources/fab_sms_test_data.xml'
    # Your input file
    output_file = "../parser-core/src/test/resources/fab_sms_test_data_anonymized.xml"  # Output file
    try:
        anonymize_xml(input_file, output_file)
    except FileNotFoundError:
        print(f"Error: Could not find {input_file}")
        print("Please make sure the input file exists in the same directory as this script.")
    except Exception as e:
        print(f"Error: {e}")
# Usage